package com.atguigu.exam.service;

import com.atguigu.exam.entity.AnswerRecord;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionAnswer;
import com.atguigu.exam.mapper.AnswerRecordMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.mapper.QuestionAnswerMapper;
import com.atguigu.exam.vo.GradingResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 主观题异步批改服务 —— 将耗时的 AI 判卷从 HTTP 请求线程中剥离
 *
 * 设计要点：
 * 1. 使用 gradingExecutor 线程池异步执行，不阻塞 HTTP 请求线程
 * 2. 使用 REQUIRES_NEW 事务隔离，避免与调用方事务互相影响
 * 3. 每道主观题独立评分，单题失败不影响其他题目
 * 4. 全部完成后更新考试记录状态为"已批阅"
 */
@Slf4j
@Service
public class AsyncGradingService {

    @Autowired
    private KimiGradingService kimiGradingService;
    @Autowired
    private AnswerRecordMapper answerRecordMapper;
    @Autowired
    private ExamRecordMapper examRecordMapper;
    @Autowired
    private QuestionAnswerMapper questionAnswerMapper;
    @Autowired
    private PaperService paperService;

    /**
     * 异步执行主观题 AI 批阅
     * 在独立线程 + 独立事务中运行，调用方的提交/回滚不会影响此方法
     *
     * @param examRecordId 考试记录ID
     * @param paperId      试卷ID（用于加载题目和答案信息）
     */
    @Async("gradingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void gradeSubjectiveQuestions(Integer examRecordId, Integer paperId) {
        log.info("异步批阅开始 examRecordId={}, 线程={}", examRecordId, Thread.currentThread().getName());

        // 1. 重新加载待批阅的答题记录（isCorrect=2）
        List<AnswerRecord> pendingRecords = answerRecordMapper.selectList(
                new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getExamRecordId, examRecordId)
                        .eq(AnswerRecord::getIsCorrect, 2));
        if (pendingRecords.isEmpty()) {
            log.info("异步批阅 examRecordId={}: 无待批阅主观题", examRecordId);
            finalizeGrading(examRecordId);
            return;
        }

        // 2. 加载试卷题目及答案信息
        Paper paper = paperService.getPaperWithQuestions(paperId);
        if (paper.getQuestions() == null || paper.getQuestions().isEmpty()) {
            log.error("异步批阅 examRecordId={}: 试卷题目为空", examRecordId);
            finalizeGrading(examRecordId);
            return;
        }
        Map<Long, Question> questionMap = paper.getQuestions().stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        // 3. 逐题 AI 评分
        for (AnswerRecord ar : pendingRecords) {
            Question question = questionMap.get(ar.getQuestionId().longValue());
            if (question == null) {
                log.warn("异步批阅 examRecordId={}: 题目ID={} 未找到", examRecordId, ar.getQuestionId());
                continue;
            }

            QuestionAnswer textAnswer = question.getAnswer();
            String standardAnswer = textAnswer != null ? textAnswer.getAnswer() : null;
            String scoreKeywords = textAnswer != null ? textAnswer.getKeywords() : null;
            Integer maxScore = question.getPaperScore() != null
                    ? question.getPaperScore().intValue() : question.getScore();

            try {
                GradingResult gradingResult = kimiGradingService.gradeTextQuestion(
                        question.getTitle(), standardAnswer, scoreKeywords,
                        ar.getUserAnswer(), maxScore != null ? maxScore : 10);

                if (gradingResult != null && gradingResult.getScore() != null) {
                    ar.setIsCorrect(3);
                    ar.setScore(gradingResult.getScore());
                    // 拼接 AI 反馈
                    StringBuilder aiFeedback = new StringBuilder();
                    if (gradingResult.getComment() != null) {
                        aiFeedback.append(gradingResult.getComment());
                    }
                    if (gradingResult.getKeyPoints() != null && !gradingResult.getKeyPoints().isEmpty()) {
                        aiFeedback.append("\n\n【得分要点】");
                        for (String kp : gradingResult.getKeyPoints()) {
                            aiFeedback.append("\n- ").append(kp);
                        }
                    }
                    ar.setAiCorrection(aiFeedback.toString());
                    log.info("异步批阅完成 examRecordId={}, 题目ID={}, 得分={}/{}",
                            examRecordId, question.getId(), gradingResult.getScore(), maxScore);
                } else {
                    // AI 评分失败，保持 isCorrect=2（待人工评阅）
                    log.warn("异步批阅失败 examRecordId={}, 题目ID={}, 保持待人工评阅", examRecordId, question.getId());
                }
            } catch (Exception e) {
                log.error("异步批阅异常 examRecordId={}, 题目ID={}, 错误: {}",
                        examRecordId, question.getId(), e.getMessage());
                // 保持 isCorrect=2，不中断其他题目的批阅
            }

            // 逐题更新答题记录（即时提交，避免全部重试）
            answerRecordMapper.updateById(ar);
        }

        // 4. 全部主观题处理完毕，更新考试记录总分和状态
        finalizeGrading(examRecordId);
    }

    /**
     * 汇总总分并标记考试为"已批阅"
     */
    private void finalizeGrading(Integer examRecordId) {
        ExamRecord record = examRecordMapper.selectById(examRecordId);
        if (record == null) {
            return;
        }
        // 重新计算总分（客观题 + 主观题）
        List<AnswerRecord> allRecords = answerRecordMapper.selectList(
                new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getExamRecordId, examRecordId));
        int totalScore = allRecords.stream()
                .mapToInt(ar -> ar.getScore() != null ? ar.getScore() : 0)
                .sum();
        record.setScore(totalScore);
        record.setStatus("已批阅");
        examRecordMapper.updateById(record);
        log.info("异步批阅全部完成 examRecordId={}, 总分={}", examRecordId, totalScore);
    }
}
