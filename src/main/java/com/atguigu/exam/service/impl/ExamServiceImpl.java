package com.atguigu.exam.service.impl;

import com.atguigu.exam.common.BusinessException;
import com.atguigu.exam.common.ErrorCode;
import com.atguigu.exam.context.CurrentUser;
import com.atguigu.exam.context.UserContext;
import com.atguigu.exam.entity.*;
import com.atguigu.exam.mapper.AnswerRecordMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.mapper.QuestionAnswerMapper;
import com.atguigu.exam.event.ExamGradedEvent;
import com.atguigu.exam.service.AsyncGradingService;
import com.atguigu.exam.service.ExamService;
import com.atguigu.exam.service.PaperService;
import com.atguigu.exam.service.QuestionScoreAsyncService;
import com.atguigu.exam.vo.StartExamVo;
import com.atguigu.exam.vo.SubmitAnswerVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * 考试服务实现类
 */
@Service
@Slf4j
public class ExamServiceImpl extends ServiceImpl<ExamRecordMapper, ExamRecord> implements ExamService {

    @Autowired
    private PaperService paperService;
    @Autowired
    private AnswerRecordMapper answerRecordMapper;
    @Autowired
    private QuestionAnswerMapper questionAnswerMapper;
    @Autowired
    private AsyncGradingService asyncGradingService;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private QuestionScoreAsyncService questionScoreAsyncService;

    // ==================== 分布式锁 Key 前缀 ====================
    private static final String LOCK_SUBMIT = "exam:lock:submit:";
    private static final String LOCK_GRADE = "exam:lock:grade:";

    /**
     * 开始考试：校验试卷已发布 -> 创建考试记录 -> 返回试卷题目
     * 
     * 重要改动：studentName 和 userId 自动从当前登录用户获取，
     * 不再由前端传参，避免伪造身份。
     */
    @Override
    @Transactional
    public ExamRecord startExam(StartExamVo startExamVo) {
        // 1. 获取试卷详情
        Paper paper = paperService.getPaperWithQuestions(startExamVo.getPaperId());
        if (!"PUBLISHED".equals(paper.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "试卷未发布，无法开始考试");
        }

        // 2. 从 JWT 上下文自动获取当前登录用户信息（不再依赖前端传参！）
        CurrentUser currentUser = UserContext.require();
        Long userId = currentUser.getUserId();
        String studentName = currentUser.getRealName();  // 真实姓名
        if (studentName == null || studentName.isEmpty()) {
            studentName = currentUser.getUsername();     // 备用：登录账号
        }
        String studentNo = currentUser.getStudentNo();   // 学号

        // 3. 创建考试记录（自动绑定用户身份）
        ExamRecord record = new ExamRecord();
        record.setExamId(startExamVo.getPaperId());
        record.setUserId(userId);
        record.setStudentNo(studentNo);   // 冗余保存学号
        record.setStudentName(studentName);
        record.setStartTime(LocalDateTime.now());
        record.setStatus("进行中");
        record.setWindowSwitches(0);
        save(record);
        
        record.setPaper(paper);
        log.info("考生 {} (学号={}, userId={}) 开始考试，考试记录ID：{}，试卷：{}", 
                studentName, studentNo, userId, record.getId(), paper.getName());
        return record;
    }

    /**
     * 提交答案：批量保存答题记录 -> 标记考试已完成
     * 
     * 分布式锁：以 userId + examId 为 key，防止重复提交
     */
    @Override
    @Transactional
    public void submitAnswers(Integer examRecordId, List<SubmitAnswerVo> answers) {
        ExamRecord record = getById(examRecordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "考试记录不存在");
        }

        Long userId = UserContext.getUserId();
        String lockKey = LOCK_SUBMIT + userId + ":" + record.getExamId();
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "系统繁忙，请稍后重试");
        }

        if (!acquired) {
            log.warn("重复提交被拦截: userId={}, examId={}, recordId={}", 
                    userId, record.getExamId(), examRecordId);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请勿重复提交");
        }

        try {
            if (!"进行中".equals(record.getStatus())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, 
                        "考试状态异常，当前状态：" + record.getStatus());
            }

            List<AnswerRecord> answerRecords = new ArrayList<>();
            for (SubmitAnswerVo answer : answers) {
                answerRecords.add(new AnswerRecord(
                        examRecordId, answer.getQuestionId(), answer.getUserAnswer()));
            }
            for (AnswerRecord answerRecord : answerRecords) {
                answerRecordMapper.insert(answerRecord);
            }

            record.setStatus("已完成");
            record.setEndTime(LocalDateTime.now());
            updateById(record);
            
            log.info("考试记录 {} 提交答案完成，共 {} 道题", examRecordId, answers.size());
            
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 自动批阅：客观题同步即时判分 + 简答题提交异步 AI 批阅
     * 
     * 新增：答对的题目自动更新 Redis 热门题目计数
     */
    @Override
    @Transactional
    public ExamRecord gradeExam(Integer examRecordId) {
        ExamRecord record = getById(examRecordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "考试记录不存在");
        }

        Long userId = UserContext.getUserId();
        String lockKey = LOCK_GRADE + userId + ":" + record.getExamId();
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "系统繁忙，请稍后重试");
        }

        if (!acquired) {
            log.warn("重复批阅被拦截: userId={}, examId={}, recordId={}", 
                    userId, record.getExamId(), examRecordId);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "试卷正在批阅中，请勿重复操作");
        }

        try {
            if (!"已完成".equals(record.getStatus())) {
                return record;
            }

            List<AnswerRecord> answerRecords = answerRecordMapper.selectList(
                    new LambdaQueryWrapper<AnswerRecord>()
                            .eq(AnswerRecord::getExamRecordId, examRecordId));
            
            if (answerRecords.isEmpty()) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "未找到答题记录");
            }

            Paper paper = paperService.getPaperWithQuestions(record.getExamId());
            List<Question> questions = paper.getQuestions();

            Map<Integer, Question> questionMap = questions.stream()
                    .collect(Collectors.toMap(q -> q.getId().intValue(), q -> q, (a, b) -> a));

            int objectiveScore = 0;
            boolean hasSubjective = false;

            for (AnswerRecord ar : answerRecords) {
                Question question = questionMap.get(ar.getQuestionId());
                if (question == null) {
                    continue;
                }

                String type = question.getType();
                String userAnswer = ar.getUserAnswer();

                if ("CHOICE".equals(type) || "JUDGE".equals(type)) {
                    // 客观题：本地即时判分
                    String correctAnswerStr = question.getAnswer() != null 
                            ? question.getAnswer().getAnswer() : null;
                    
                    if ("JUDGE".equals(type)) {
                        userAnswer = normalizeJudgeAnswer(userAnswer);
                    }

                    if (correctAnswerStr != null && correctAnswerStr.equalsIgnoreCase(userAnswer)) {
                        ar.setIsCorrect(1);
                        Integer score = question.getPaperScore() != null
                                ? question.getPaperScore().intValue() : question.getScore();
                        ar.setScore(score);
                        objectiveScore += score != null ? score : 0;
                        // 答对 -> 更新 Redis 热门题目计数
                        questionScoreAsyncService.incrementQuestionScore(question.getId());
                    } else {
                        ar.setIsCorrect(0);
                        ar.setScore(0);
                    }
                } else {
                    // 主观题：标记待批阅，提交异步 AI 评分
                    ar.setIsCorrect(2);
                    ar.setScore(0);
                    hasSubjective = true;
                }
                answerRecordMapper.updateById(ar);
            }

            record.setScore(objectiveScore);
            if (hasSubjective) {
                record.setStatus("批阅中");
                updateById(record);
                // 发布事件，由 @TransactionalEventListener 在事务提交后触发异步批阅
                eventPublisher.publishEvent(new ExamGradedEvent(examRecordId, record.getExamId()));
                log.info("考试记录 {} 已发布批阅事件，等待事务提交后触发AI批阅", examRecordId);
                log.info("考试记录 {} 客观题批阅完成（{}分），主观题已提交异步批阅", 
                        examRecordId, objectiveScore);
            } else {
                record.setStatus("已批阅");
                updateById(record);
                log.info("考试记录 {} 批阅完成（纯客观题），总分：{}", examRecordId, objectiveScore);
            }

            record.setAnswerRecords(answerRecords);
            return record;

        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 获取考试记录详情（含答题明细 + 试卷信息）
     */
    @Override
    public ExamRecord getExamRecordDetail(Integer id) {
        ExamRecord record = getById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "考试记录不存在");
        }
        List<AnswerRecord> answerRecords = answerRecordMapper.selectList(
                new LambdaQueryWrapper<AnswerRecord>().eq(AnswerRecord::getExamRecordId, id));
        record.setAnswerRecords(answerRecords);
        Paper paper = paperService.getPaperWithQuestions(record.getExamId());
        record.setPaper(paper);
        return record;
    }

    /**
     * 获取当前登录用户的所有考试记录
     * 只返回当前用户自己的记录，按创建时间倒序
     */
    @Override
    public List<ExamRecord> getMyRecords() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return List.of();  // 未登录返回空列表
        }
        LambdaQueryWrapper<ExamRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamRecord::getUserId, userId)
               .orderByDesc(ExamRecord::getCreateTime);
        return list(wrapper);
    }

    /**
     * 获取所有考试记录（管理员用）
     */
    @Override
    public List<ExamRecord> getRecords() {
        LambdaQueryWrapper<ExamRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ExamRecord::getCreateTime);
        return list(wrapper);
    }

    private String normalizeJudgeAnswer(String answer) {
        if (answer == null) {
            return null;
        }
        String trimmed = answer.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        if ("T".equalsIgnoreCase(trimmed) || "TRUE".equalsIgnoreCase(trimmed)
                || "1".equals(trimmed) || "YES".equalsIgnoreCase(trimmed)
                || "Y".equalsIgnoreCase(trimmed)
                || "正确".equals(trimmed) || "对".equals(trimmed)) {
            return "TRUE";
        }
        if ("F".equalsIgnoreCase(trimmed) || "FALSE".equalsIgnoreCase(trimmed)
                || "0".equals(trimmed) || "NO".equalsIgnoreCase(trimmed)
                || "N".equalsIgnoreCase(trimmed)
                || "错误".equals(trimmed) || "错".equals(trimmed)) {
            return "FALSE";
        }
        return trimmed;
    }
}