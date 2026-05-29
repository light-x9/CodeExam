package com.atguigu.exam.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.exam.config.properties.KimiApiProperties;
import com.atguigu.exam.service.KimiGradingService;
import com.atguigu.exam.vo.GradingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * Kimi AI 判卷服务实现 —— 对简答题进行智能评分和点评
 *
 * 调用链：ExamServiceImpl.gradeExam() → gradeTextQuestion() → Kimi API → 解析 JSON → 返回评分
 * 错误处理：最多重试 3 次，全部失败后返回 null，由调用方标记为"待人工评阅"
 */
@Slf4j
@Service
public class KimiGradingServiceImpl implements KimiGradingService {

    @Autowired
    private WebClient webClient;

    @Autowired
    private KimiApiProperties kimiApiProperties;

    /**
     * 最大重试次数 —— AI 调用不稳定的情况下给予一定容错
     */
    private static final int MAX_RETRY = 3;

    /**
     * 重试间隔（毫秒）—— 避免触发 Kimi API 的速率限制
     */
    private static final long RETRY_DELAY_MS = 1000;

    @Override
    public GradingResult gradeTextQuestion(String questionTitle, String standardAnswer,
                                            String keywords, String studentAnswer, Integer maxScore) {
        // 1. 构建判卷 Prompt
        String prompt = buildGradingPrompt(questionTitle, standardAnswer, keywords,
                studentAnswer, maxScore);

        // 2. 带重试的 AI 调用
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                // 2.1 调用 Kimi API
                String responseJson = callKimiAPI(prompt);

                // 2.2 解析 AI 返回的 JSON 为 GradingResult
                GradingResult result = parseGradingResponse(responseJson, maxScore);

                if (result != null) {
                    log.info("AI判卷成功: 学生得分={}/{}, 命中要点={}",
                            result.getScore(), maxScore, result.getKeyPoints());
                    return result;
                }

                log.warn("AI判卷返回数据为空或解析失败，第{}次尝试", attempt);

            } catch (Exception e) {
                log.error("AI判卷调用失败，第{}次尝试，错误: {}", attempt, e.getMessage());
            }

            // 2.3 非最后一次 → 等待后重试；最后一次 → 返回 null（降级）
            if (attempt < MAX_RETRY) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // 3. 所有重试都失败 → 返回 null，由调用方标记为"待人工评阅"
        log.error("AI判卷全部{}次重试失败，降级为待人工评阅", MAX_RETRY);
        return null;
    }

    // ======================== 私有方法 ========================

    /**
     * 构建发送给 AI 的判卷 Prompt
     *
     * Prompt 设计原则：
     * 1. 明确 AI 角色（专业评卷老师）
     * 2. 提供完整参考信息（题目、标准答案、关键词、满分值）
     * 3. 要求严格的 JSON 输出格式（便于程序解析）
     * 4. 给出具体评分指引（避免 AI 判分过严或过松）
     */
    private String buildGradingPrompt(String questionTitle, String standardAnswer,
                                       String keywords, String studentAnswer, Integer maxScore) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一位专业的考试评卷老师。请对以下学生的简答题答案进行客观评分。\n\n");

        // 题目信息
        prompt.append("【题目】\n").append(questionTitle).append("\n\n");

        // 标准答案（参考依据）
        prompt.append("【标准答案】\n");
        if (standardAnswer != null && !standardAnswer.trim().isEmpty()) {
            prompt.append(standardAnswer).append("\n\n");
        } else {
            prompt.append("（未提供标准答案，请根据评分关键词和专业知识判断）\n\n");
        }

        // 评分关键词（核心得分点）
        prompt.append("【评分关键词】\n");
        if (keywords != null && !keywords.trim().isEmpty()) {
            prompt.append(keywords).append("\n\n");
        } else {
            prompt.append("（未提供关键词，请根据标准答案内容自行判断得分要点）\n\n");
        }

        // 满分分值
        prompt.append("【满分分值】\n").append(maxScore).append(" 分\n\n");

        // 学生答案
        prompt.append("【学生答案】\n");
        if (studentAnswer != null && !studentAnswer.trim().isEmpty()) {
            prompt.append(studentAnswer).append("\n\n");
        } else {
            prompt.append("（学生未作答）\n\n");
        }

        // JSON 格式要求
        prompt.append("请严格按照以下 JSON 格式返回评分结果，不要包含任何其他文字或标记：\n");
        prompt.append("{\n");
        prompt.append("  \"score\": ").append(maxScore / 2)
                .append(",\n"); // 示例分数用满分一半，作为格式参考
        prompt.append("  \"comment\": \"详细评语，具体指出答案的优缺点和遗漏的知识点\",\n");
        prompt.append("  \"keyPoints\": [\"学生命中的要点1\", \"学生遗漏的要点2\"]\n");
        prompt.append("}\n\n");

        // 评分指引
        prompt.append("评分要求：\n");
        prompt.append("1. 得分范围：0 到 ").append(maxScore).append(" 分（整数）\n");
        prompt.append("2. 根据关键词匹配度和答案完整性综合评分，核心意思一致即可得分，不要求逐字相同\n");
        prompt.append("3. 如果学生答案完全偏离主题或未作答，给 0 分\n");
        prompt.append("4. 评语要具体、有建设性，指出答对的知识点和遗漏的内容\n");
        prompt.append("5. keyPoints 数组列出学生实际命中的得分要点\n");
        prompt.append("6. 评分尺度适中，既不过严也不过松\n");

        return prompt.toString();
    }

    /**
     * 调用 Kimi API，返回原始 JSON 字符串
     *
     * 复用项目中已有的 WebClient Bean（WebClientConfiguration 配置了 baseUrl 和 Authorization）
     * 请求体格式遵循 OpenAI Chat Completions API 规范（Kimi API 兼容该格式）
     */
    private String callKimiAPI(String prompt) {
        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", kimiApiProperties.getModel());
        requestBody.put("temperature", kimiApiProperties.getTemperature());
        requestBody.put("max_tokens", kimiApiProperties.getMaxTokens());

        // 构建 messages（system + user 双角色）
        List<Map<String, Object>> messages = new ArrayList<>();

        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是一位专业的考试评卷老师，你的回答必须是严格的 JSON 格式，不包含任何其他文字。");
        messages.add(systemMessage);

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);

        requestBody.put("messages", messages);

        // 发起 HTTP POST 请求（同步阻塞，适配当前同步判卷流程）
        String response = webClient
                .post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        // 检查 API 是否返回错误
        JSONObject resultObject = JSONObject.parseObject(response);
        if (resultObject.containsKey("error")) {
            String errorMessage = resultObject.getJSONObject("error").getString("message");
            throw new RuntimeException("Kimi API 返回错误: " + errorMessage);
        }

        // 提取 choices[0].message.content
        String content = resultObject
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");

        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("Kimi API 返回的 content 为空");
        }

        return content.trim();
    }

    /**
     * 解析 AI 返回的 JSON，提取评分信息
     *
     * AI 可能返回包含 markdown 代码块的文本（如 ```json ... ```），
     * 这里做容错处理：先尝试提取 JSON 块，再解析
     */
    private GradingResult parseGradingResponse(String rawContent, Integer maxScore) {
        // 1. 清洗 AI 输出 —— 提取纯 JSON（处理 markdown 代码块包裹的情况）
        String jsonStr = extractJson(rawContent);
        if (jsonStr == null) {
            log.error("无法从AI返回中提取JSON: {}", rawContent);
            return null;
        }

        try {
            // 2. 解析 JSON
            JSONObject json = JSONObject.parseObject(jsonStr);

            GradingResult result = new GradingResult();

            // 2.1 提取分数（带范围校验）
            Integer score = json.getInteger("score");
            if (score == null) {
                log.error("AI返回的JSON缺少score字段: {}", jsonStr);
                return null;
            }
            // 分数不能超过满分，也不能为负数
            result.setScore(Math.max(0, Math.min(score, maxScore)));

            // 2.2 提取评语
            result.setComment(json.getString("comment"));

            // 2.3 提取得分要点
            JSONArray keyPointsArray = json.getJSONArray("keyPoints");
            if (keyPointsArray != null && !keyPointsArray.isEmpty()) {
                List<String> keyPoints = new ArrayList<>();
                for (int i = 0; i < keyPointsArray.size(); i++) {
                    keyPoints.add(keyPointsArray.getString(i));
                }
                result.setKeyPoints(keyPoints);
            }

            return result;

        } catch (Exception e) {
            log.error("解析AI判卷返回JSON失败: {}，原始内容: {}", e.getMessage(), rawContent);
            return null;
        }
    }

    /**
     * 从 AI 返回的文本中提取 JSON 字符串
     *
     * 容错策略：
     * 1. 如果内容被 ```json ... ``` 包裹 → 提取中间部分
     * 2. 否则直接尝试解析整个返回内容
     */
    private String extractJson(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        // 处理 markdown 代码块：```json ... ```
        int jsonStart = content.indexOf("```json");
        if (jsonStart != -1) {
            int contentStart = content.indexOf('\n', jsonStart);
            if (contentStart == -1) {
                contentStart = jsonStart + "```json".length();
            }
            int jsonEnd = content.indexOf("```", contentStart);
            if (jsonEnd != -1) {
                return content.substring(contentStart, jsonEnd).trim();
            }
        }

        // 处理普通代码块：``` ... ```
        int codeStart = content.indexOf("```");
        if (codeStart != -1) {
            int contentStart = content.indexOf('\n', codeStart);
            if (contentStart == -1) {
                contentStart = codeStart + 3;
            }
            int codeEnd = content.indexOf("```", contentStart);
            if (codeEnd != -1) {
                return content.substring(contentStart, codeEnd).trim();
            }
        }

        // 处理直接包裹在 {} 中的 JSON
        int braceStart = content.indexOf('{');
        int braceEnd = content.lastIndexOf('}');
        if (braceStart != -1 && braceEnd != -1 && braceEnd > braceStart) {
            return content.substring(braceStart, braceEnd + 1).trim();
        }

        return null;
    }
}
