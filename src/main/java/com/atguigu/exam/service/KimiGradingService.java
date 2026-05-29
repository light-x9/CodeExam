package com.atguigu.exam.service;

import com.atguigu.exam.vo.GradingResult;

/**
 * Kimi AI 判卷服务
 * 使用 Kimi API 对简答题进行智能批改和点评
 */
public interface KimiGradingService {

    /**
     * 对一道简答题进行 AI 批改
     *
     * @param questionTitle  题目标题/题干
     * @param standardAnswer 标准答案（可为空，为空时仅依据关键词评分）
     * @param keywords       评分关键词，多个用逗号分隔（可为空）
     * @param studentAnswer  学生提交的答案
     * @param maxScore       题目满分分值
     * @return 判卷结果（含分数、评语、得分要点）；AI 调用失败时返回 null
     */
    GradingResult gradeTextQuestion(String questionTitle, String standardAnswer,
                                     String keywords, String studentAnswer, Integer maxScore);
}
