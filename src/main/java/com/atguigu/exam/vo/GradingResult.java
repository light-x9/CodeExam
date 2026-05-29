package com.atguigu.exam.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 判卷返回结果 DTO
 * 用于解析 Kimi API 返回的 JSON 评分数据
 */
@Data
public class GradingResult implements Serializable {

    /**
     * AI 给出的得分（0 到 maxScore 之间的整数）
     */
    private Integer score;

    /**
     * AI 评语 —— 详细指出答案优缺点、遗漏的知识点
     */
    private String comment;

    /**
     * 得分要点列表 —— 学生命中/未命中的关键词
     */
    private List<String> keyPoints;

    private static final long serialVersionUID = 1L;
}
