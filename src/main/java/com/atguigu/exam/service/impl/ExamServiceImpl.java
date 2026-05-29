package com.atguigu.exam.service.impl;

import com.atguigu.exam.common.BusinessException;
import com.atguigu.exam.common.ErrorCode;
import com.atguigu.exam.entity.*;
import com.atguigu.exam.mapper.AnswerRecordMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.mapper.QuestionAnswerMapper;
import com.atguigu.exam.service.AsyncGradingService;
import com.atguigu.exam.service.ExamService;
import com.atguigu.exam.service.PaperService;
import com.atguigu.exam.vo.StartExamVo;
import com.atguigu.exam.vo.SubmitAnswerVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /**
     * 开始考试：校验试卷已发布 → 创建考试记录 → 返回试卷题目
     */
    @Override
    @Transactional
    public ExamRecord startExam(StartExamVo startExamVo) {
        // 1. 获取试卷详情
        Paper paper = paperService.getPaperWithQuestions(startExamVo.getPaperId());
        // 2. 只有已发布的试卷才能参加考试
        if (!"PUBLISHED".equals(paper.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "试卷未发布，无法开始考试");
        }
        // 3. 创建考试记录
        ExamRecord record = new ExamRecord();
        record.setExamId(startExamVo.getPaperId());
        record.setStudentName(startExamVo.getStudentName());
        record.setStartTime(LocalDateTime.now());
        record.setStatus("进行中");
        record.setWindowSwitches(0);
        save(record);
        // 4. 返回试卷题目（不返回答案）
        record.setPaper(paper);
        log.info("考生 {} 开始考试，考试记录ID：{}，试卷：{}", startExamVo.getStudentName(), record.getId(), paper.getName());
        return record;
    }

    /**
     * 提交答案：批量保存答题记录 → 标记考试已完成
     */
    @Override
    @Transactional
    public void submitAnswers(Integer examRecordId, List<SubmitAnswerVo> answers) {
        // 1. 校验考试记录存在且状态正确
        ExamRecord record = getById(examRecordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "考试记录不存在");
        }
        if (!"进行中".equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "考试状态异常，当前状态：" + record.getStatus());
        }
        // 2. 批量保存答题记录
        List<AnswerRecord> answerRecords = new ArrayList<>();
        for (SubmitAnswerVo answer : answers) {
            answerRecords.add(new AnswerRecord(examRecordId, answer.getQuestionId(), answer.getUserAnswer()));
        }
        for (AnswerRecord answerRecord : answerRecords) {
            answerRecordMapper.insert(answerRecord);
        }
        // 3. 标记考试已完成
        record.setStatus("已完成");
        record.setEndTime(LocalDateTime.now());
        updateById(record);
        log.info("考试记录 {} 提交答案完成，共 {} 道题", examRecordId, answers.size());
    }

    /**
     * 自动批阅：客观题（选择/判断）同步即时判分 + 简答题提交异步 AI 批阅
     *
     * 设计思路：
     * 1. 客观题本地字符串比对，毫秒级完成，结果即时写入数据库
     * 2. 主观题标记为"待批阅"（isCorrect=2），提交到 gradingExecutor 线程池异步处理
     * 3. HTTP 请求不再等待 AI 调用，客观题结果立即可见
     * 4. 异步任务完成后自动更新考试状态为"已批阅"并汇总总分
     *
     * @param examRecordId 考试记录ID
     * @return 批阅中的考试记录（客观题已判分，主观题待异步批阅）
     */
    @Override
    public ExamRecord gradeExam(Integer examRecordId) {
        // 1. 校验考试记录存在
        ExamRecord record = getById(examRecordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "考试记录不存在");
        }
        // 2. 获取试卷题目及答案
        Paper paper = paperService.getPaperWithQuestions(record.getExamId());
        if (paper.getQuestions() == null || paper.getQuestions().isEmpty()) {
            throw new BusinessException(ErrorCode.PAPER_QUESTION_EMPTY);
        }
        // 3. 获取该次考试的所有答题记录
        List<AnswerRecord> answerRecords = answerRecordMapper.selectList(
                new LambdaQueryWrapper<AnswerRecord>().eq(AnswerRecord::getExamRecordId, examRecordId));
        // 4. 构建题目ID → Question 的映射
        Map<Long, Question> questionMap = paper.getQuestions().stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        int objectiveScore = 0;   // 客观题得分累计
        boolean hasSubjective = false; // 是否存在主观题需要异步批改

        // 5. 逐题判分
        for (AnswerRecord ar : answerRecords) {
            Question question = questionMap.get(ar.getQuestionId().longValue());
            if (question == null) {
                ar.setIsCorrect(0);
                ar.setScore(0);
                continue;
            }
            String type = question.getType();
            if ("CHOICE".equals(type) || "JUDGE".equals(type)) {
                // ── 客观题：本地即时判分 ──
                QuestionAnswer correctAnswer = question.getAnswer();
                String userAnswer = ar.getUserAnswer();
                String correctAnswerStr = correctAnswer != null ? correctAnswer.getAnswer() : null;

                if ("JUDGE".equals(type)) {
                    log.info("判卷调试 - 题目ID={}, 原始标准答案={}, 原始学生答案={}",
                            question.getId(), correctAnswerStr, userAnswer);
                    userAnswer = normalizeJudgeAnswer(userAnswer);
                    correctAnswerStr = normalizeJudgeAnswer(correctAnswerStr);
                    log.info("判卷调试 - 题目ID={}, 归一化标准答案={}, 归一化学生答案={}",
                            question.getId(), correctAnswerStr, userAnswer);
                }

                if (correctAnswerStr != null && correctAnswerStr.equalsIgnoreCase(userAnswer)) {
                    ar.setIsCorrect(1);
                    Integer score = question.getPaperScore() != null
                            ? question.getPaperScore().intValue() : question.getScore();
                    ar.setScore(score);
                    objectiveScore += score != null ? score : 0;
                } else {
                    ar.setIsCorrect(0);
                    ar.setScore(0);
                }
            } else {
                // ── 主观题：标记待批阅，提交异步 AI 评分 ──
                // isCorrect=2 表示待人工/AI评阅
                ar.setIsCorrect(2);
                ar.setScore(0);
                hasSubjective = true;
            }
            // 逐题更新，即时落库（无 @Transactional，MyBatis-Plus 自动提交）
            answerRecordMapper.updateById(ar);
        }

        // 6. 更新考试记录
        record.setScore(objectiveScore);
        if (hasSubjective) {
            record.setStatus("批阅中");
            updateById(record);
            // 提交异步批阅任务 —— HTTP 请求线程立即返回，不等待 AI 调用
            asyncGradingService.gradeSubjectiveQuestions(examRecordId, record.getExamId());
            log.info("考试记录 {} 客观题批阅完成（{}分），主观题已提交异步批阅", examRecordId, objectiveScore);
        } else {
            record.setStatus("已批阅");
            updateById(record);
            log.info("考试记录 {} 批阅完成（纯客观题），总分：{}", examRecordId, objectiveScore);
        }

        // 7. 回填答题记录列表后返回
        record.setAnswerRecords(answerRecords);
        return record;
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
        // 加载答题记录
        List<AnswerRecord> answerRecords = answerRecordMapper.selectList(
                new LambdaQueryWrapper<AnswerRecord>().eq(AnswerRecord::getExamRecordId, id));
        record.setAnswerRecords(answerRecords);
        // 加载试卷信息
        Paper paper = paperService.getPaperWithQuestions(record.getExamId());
        record.setPaper(paper);
        return record;
    }

    /**
     * 获取所有考试记录
     */
    @Override
    public List<ExamRecord> getRecords() {
        return list();
    }

    /**
     * 统一判断题答案格式：将各种正确/错误的写法归一化为 TRUE / FALSE
     * 支持：T/F、TRUE/FALSE、true/false、1/0、YES/NO、Y/N、正确/错误、对/错
     * @param answer 原始答案字符串
     * @return 归一化后的答案（TRUE 或 FALSE），无法识别时返回原值（去空格）
     */
    private String normalizeJudgeAnswer(String answer) {
        if (answer == null) {
            return null;
        }
        String trimmed = answer.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        // 正确的各种写法 → TRUE
        if ("T".equalsIgnoreCase(trimmed) || "TRUE".equalsIgnoreCase(trimmed)
                || "1".equals(trimmed) || "YES".equalsIgnoreCase(trimmed)
                || "Y".equalsIgnoreCase(trimmed)
                || "正确".equals(trimmed) || "对".equals(trimmed)
                || "✓".equals(trimmed) || "√".equals(trimmed)) {
            return "TRUE";
        }
        // 错误的各种写法 → FALSE
        if ("F".equalsIgnoreCase(trimmed) || "FALSE".equalsIgnoreCase(trimmed)
                || "0".equals(trimmed) || "NO".equalsIgnoreCase(trimmed)
                || "N".equalsIgnoreCase(trimmed)
                || "错误".equals(trimmed) || "错".equals(trimmed)
                || "✗".equals(trimmed) || "×".equals(trimmed)) {
            return "FALSE";
        }
        // 无法识别的格式，返回原值（已去空格）
        log.warn("判断题答案格式无法识别: [{}]，将使用原值比较", trimmed);
        return trimmed;
    }
} 