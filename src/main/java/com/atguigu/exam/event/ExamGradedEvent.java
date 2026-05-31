package com.atguigu.exam.event;

import lombok.Getter;

/**
 * 考试客观题批阅完成事件
 * <p>
 * 在 gradeExam() 事务提交后发布，通知 AsyncGradingService 开始异步 AI 批阅主观题。
 * 确保异步线程能在主事务提交后查到 is_correct=2 的待批阅记录。
 */
@Getter
public class ExamGradedEvent {

    /** 考试记录ID */
    private final Integer examRecordId;

    /** 试卷ID（用于加载题目和答案） */
    private final Integer paperId;

    public ExamGradedEvent(Integer examRecordId, Integer paperId) {
        this.examRecordId = examRecordId;
        this.paperId = paperId;
    }
}