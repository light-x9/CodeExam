package com.atguigu.exam.service;


import com.atguigu.exam.vo.AiGenerateRequestVo;

/**
 * Kimi AI服务接口
 * 用于调用Kimi API生成题目
 */
public interface KimiAiService {
    /**
     * 封装请求kimi模型的方法
     * @param prompt 提示词
     * @return 模型反馈的结果
     */
    String callKimiAI(String prompt) throws InterruptedException;

    /**
     * 根据生成请求参数，构建AI提示词
     * @param request AI生成题目请求参数
     * @return 拼接完成的提示词字符串
     */
    String buildPrompt(AiGenerateRequestVo request);
} 