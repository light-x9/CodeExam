package com.atguigu.exam.service;

import com.atguigu.exam.entity.AnswerRecord;
import com.atguigu.exam.entity.ExamRecord;
import com.atguigu.exam.entity.Paper;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionAnswer;
import com.atguigu.exam.event.ExamGradedEvent;
import com.atguigu.exam.mapper.AnswerRecordMapper;
import com.atguigu.exam.mapper.ExamRecordMapper;
import com.atguigu.exam.mapper.QuestionAnswerMapper;
import com.atguigu.exam.vo.GradingResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AI 主观题异步批改服务
 * <p>
 * 核心流程：
 * 1. ExamServiceImpl.gradeExam() 发布 ExamGradedEvent
 * 2. @TransactionalEventListener(phase = AFTER_COMMIT) 确保主事务提交后才触发
 * 3. CompletableFuture.runAsync 在 gradingExecutor 线程池中异步执行 AI 批阅
 * 4. 每道主观题独立调用 Kimi/DeepSeek API 评分，单题失败不影响其他
 * 5. 全部完成后汇总总分，更新考试记录状态为"已批阅"
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
    @Autowired
    private ThreadPoolTaskExecutor gradingExecutor;

    /**
     * 监听考试批阅事件 —— 在 gradeExam() 事务提交后触发
     * <p>
     * AFTER_COMMIT 保证：
     * - is_correct=2 的待批阅记录已持久化到数据库
     * - 异步线程能查到这些记录
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleExamGraded(ExamGradedEvent event) {
        Integer examRecordId = event.getExamRecordId();
        Integer paperId = event.getPaperId();
        log.info("收到批阅事件 examRecordId={}, 提交异步AI批阅任务", examRecordId);

        CompletableFuture.runAsync(() -> {
            doGradeSubjective(examRecordId, paperId);
        }, gradingExecutor).exceptionally(ex -> {
            log.error("异步批阅任务异常 examRecordId={}, 错误: {}", examRecordId, ex.getMessage(), ex);
            return null;
        });
    }

    /**
     * 实际执行 AI 批阅（在 gradingExecutor 线程池中运行）
     */
    private void doGradeSubjective(Integer examRecordId, Integer paperId) {
        log.info("异步批阅开始 examRecordId={}, 线程={}", examRecordId, Thread.currentThread().getName());
        try {
            // 1. 查询待批阅的主观题（isCorrect=2）
            List<AnswerRecord> pendingRecords = answerRecordMapper.selectList(
                    new LambdaQueryWrapper<AnswerRecord>()
                            .eq(AnswerRecord::getExamRecordId, examRecordId)
                            .eq(AnswerRecord::getIsCorrect, 2));

            log.info("异步批阅 examRecordId={}: 待批阅主观题数量={}", examRecordId, pendingRecords.size());

            if (pendingRecords.isEmpty()) {
                log.info("异步批阅 examRecordId={}: 无待批阅主观题，直接完成", examRecordId);
                finalizeGrading(examRecordId);
                return;
            }

            // 2. 加载试卷题目及答案
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
                        log.warn("AI评分失败 examRecordId={}, 题目ID={}, 保持待人工评阅", examRecordId, question.getId());
                    }
                } catch (Exception e) {
                    log.error("异步批阅异常 examRecordId={}, 题目ID={}, 错误: {}",
                            examRecordId, question.getId(), e.getMessage());
                }

                // 逐题更新（即时提交，避免全部重试）
                answerRecordMapper.updateById(ar);
            }

        } catch (Exception e) {
            log.error("异步批阅整体异常 examRecordId={}, 错误: {}", examRecordId, e.getMessage(), e);
        } finally {
            // 4. 无论成功失败，汇总总分并标记为"已批阅"
            finalizeGrading(examRecordId);
        }
    }

    /**
     * 汇总总分并更新考试记录状态为"已批阅"
     */
    private void finalizeGrading(Integer examRecordId) {
        ExamRecord record = examRecordMapper.selectById(examRecordId);
        if (record == null) {
            return;
        }
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