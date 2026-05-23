package com.atguigu.exam.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.exam.common.BusinessException;
import com.atguigu.exam.common.ErrorCode;
import com.atguigu.exam.config.properties.KimiApiProperties;
import com.atguigu.exam.service.KimiAiService;
import com.atguigu.exam.vo.AiGenerateRequestVo;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

/**
 * Kimi AI生成服务实现类
 */
@Slf4j
@Service
public class KimiAiServiceImpl implements KimiAiService {

    // 注入WebClient Bean，用于发起HTTP请求
    @Autowired
    private WebClient webClient;

    // 注入Kimi API配置属性类，读取配置文件中的API参数
    @Autowired
    private KimiApiProperties kimiApiProperties;

    /**
     * 封装请求Kimi模型的方法（带重试机制）
     * @param prompt 提示词（用户输入的问题/指令）
     * @return 模型返回的结果文本
     * @throws InterruptedException 线程睡眠时被中断抛出的异常
     */
    @Override
    public String callKimiAI(String prompt) throws InterruptedException {
        // 1. 定义重试次数：最多尝试3次
        int maxTry = 3;

        // 2. 重试循环：for + try-catch 实现服务降级，单条请求失败不中断整体流程
        for (int i = 1; i <= maxTry; i++) {
            try {
                // --------------------------
                // 2.1 构建Kimi API请求体
                // --------------------------
                Map<String, Object> requestBody = new HashMap<>();
                // 从配置类读取模型名称
                requestBody.put("model", kimiApiProperties.getModel());
                // 从配置类读取温度参数（控制输出随机性）
                requestBody.put("temperature", kimiApiProperties.getTemperature());
                // 从配置类读取最大token数（控制输出长度）
                requestBody.put("max_tokens", kimiApiProperties.getMaxTokens());

                // 构建messages数组（符合Kimi API对话格式）
                List<Map<String, Object>> messages = new ArrayList<>();
                Map<String, Object> userMessage = new HashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", prompt);
                messages.add(userMessage);
                // 将消息列表放入请求体
                requestBody.put("messages", messages);

                // --------------------------
                // 2.2 发起WebClient POST请求
                // --------------------------
                String response = webClient
                        .post() // 发起POST请求
                        .bodyValue(requestBody) // 设置请求体（自动转为JSON）
                        .retrieve() // 触发请求
                        .bodyToMono(String.class) // 将响应体转为String类型的Mono
                        .block(); // 同步阻塞获取结果（适配同步业务场景）

                // --------------------------
                // 2.3 解析响应结果（成功/失败）
                // --------------------------
                JSONObject resultObject = JSONObject.parseObject(response);

                // 情况1：响应中包含error字段 → API调用失败（如内容风控、key过期、限速）
                if (resultObject.containsKey("error")) {
                    // 提取错误信息并抛出异常，进入catch块处理重试
                    String errorMessage = resultObject.getJSONObject("error").getString("message");
                    throw new Exception(errorMessage);
                }

                // 情况2：响应正常 → 提取模型返回的content文本
                String content = resultObject
                        .getJSONArray("choices") // 获取choices数组
                        .getJSONObject(0) // 取第一个choice
                        .getJSONObject("message") // 取message对象
                        .getString("content"); // 提取content字段

                // 校验返回内容是否为空
                if (ObjectUtils.isEmpty(content)) {
                    throw new Exception("返回结果结构正确，但是返回数据为空！再次尝试！");
                }

                // 成功获取结果，直接返回，结束重试循环
                return content;

            } catch (Exception e) {
                // --------------------------
                // 3. 异常处理：记录日志 + 重试控制
                // --------------------------
                // 3.1 记录当前重试次数和错误信息，便于排查问题
                log.error("第{}次尝试调用kimi模型失败了，失败的错误信息为：{}！", i, e.getMessage());

                // 3.2 如果不是最后一次重试，线程睡眠1秒（避免触发API限速），然后继续循环
                if (i != maxTry) {
                    // 线程休眠1秒，缓解API调用频率
                    Thread.sleep(1000);
                } else {
                    // 3.3 如果已经是最后一次重试，直接抛出异常，不再继续尝试
                    throw new BusinessException(ErrorCode.AI_GENERATE_FAILED,
                        "已经重试了%s次调用kimi的模型，但是依然没有正确的返回结果！".formatted(maxTry));
                }
            }
        }

        // 4. 循环结束仍未获取到有效结果 → 抛出最终异常
        throw new BusinessException(ErrorCode.AI_GENERATE_FAILED,
            "已经重试了%s次调用kimi的模型，但是依然没有正确的返回结果！".formatted(maxTry));
    }



    /**
     * 构建发送给AI的提示词
     */
    public String buildPrompt(AiGenerateRequestVo request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("请为我生成").append(request.getCount()).append("道关于【")
                .append(request.getTopic()).append("】的题目。\n\n");

        prompt.append("要求：\n");

        // 题目类型要求
        if (request.getTypes() != null && !request.getTypes().isEmpty()) {
            List<String> typeList = Arrays.asList(request.getTypes().split(","));
            prompt.append("- 题目类型：");
            for (String type : typeList) {
                switch (type.trim()) {
                    case "CHOICE":
                        prompt.append("选择题");
                        if (request.getIncludeMultiple() != null && request.getIncludeMultiple()) {
                            prompt.append("(包含单选和多选)");
                        }
                        prompt.append(" ");
                        break;
                    case "JUDGE":
                        prompt.append("判断题（**重要：确保正确答案和错误答案的数量大致平衡，不要全部都是正确或错误**） ");
                        break;
                    case "TEXT":
                        prompt.append("简答题 ");
                        break;
                }
            }
            prompt.append("\n");
        }

        // 难度要求
        if (request.getDifficulty() != null) {
            String difficultyText = switch (request.getDifficulty()) {
                case "EASY" -> "简单";
                case "MEDIUM" -> "中等";
                case "HARD" -> "困难";
                default -> "中等";
            };
            prompt.append("- 难度等级：").append(difficultyText).append("\n");
        }

        // 额外要求
        if (request.getRequirements() != null && !request.getRequirements().isEmpty()) {
            prompt.append("- 特殊要求：").append(request.getRequirements()).append("\n");
        }

        // 判断题特别要求
        if (request.getTypes() != null && request.getTypes().contains("JUDGE")) {
            prompt.append("- **判断题特别要求**：\n");
            prompt.append("  * 确保生成的判断题中，正确答案(TRUE)和错误答案(FALSE)的数量尽量平衡\n");
            prompt.append("  * 不要所有判断题都是正确的或都是错误的\n");
            prompt.append("  * 错误的陈述应该是常见的误解或容易混淆的概念\n");
            prompt.append("  * 正确的陈述应该是重要的基础知识点\n");
        }

        prompt.append("\n请严格按照以下JSON格式返回，不要包含任何其他文字：\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"questions\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"title\": \"题目内容\",\n");
        prompt.append("      \"type\": \"CHOICE|JUDGE|TEXT\",\n");
        prompt.append("      \"multi\": true/false,\n");
        prompt.append("      \"difficulty\": \"EASY|MEDIUM|HARD\",\n");
        prompt.append("      \"score\": 5,\n");
        prompt.append("      \"choices\": [\n");
        prompt.append("        {\"content\": \"选项内容\", \"isCorrect\": true/false, \"sort\": 1}\n");
        prompt.append("      ],\n");
        prompt.append("      \"answer\": \"TRUE或FALSE(判断题专用)|文本答案(简答题专用)\",\n");
        prompt.append("      \"analysis\": \"题目解析\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        prompt.append("注意：\n");
        prompt.append("1. 选择题必须有choices数组，判断题和简答题设置answer字段\n");
        prompt.append("2. 多选题的multi字段设为true，单选题设为false\n");
        prompt.append("3. **判断题的answer字段只能是\"TRUE\"或\"FALSE\"，请确保答案分布合理**\n");
        prompt.append("4. 每道题都要有详细的解析\n");
        prompt.append("5. 题目要有实际价值，贴近实际应用场景\n");
        prompt.append("6. 严格按照JSON格式返回，确保可以正确解析\n");

        // 如果只生成判断题，额外强调答案平衡
        if (request.getTypes() != null && request.getTypes().equals("JUDGE") && request.getCount() > 1) {
            prompt.append("7. **判断题答案分布要求**：在").append(request.getCount()).append("道判断题中，");
            int halfCount = request.getCount() / 2;
            if (request.getCount() % 2 == 0) {
                prompt.append("请生成").append(halfCount).append("道正确(TRUE)和").append(halfCount).append("道错误(FALSE)的题目");
            } else {
                prompt.append("请生成约").append(halfCount).append("-").append(halfCount + 1).append("道正确(TRUE)和约").append(halfCount).append("-").append(halfCount + 1).append("道错误(FALSE)的题目");
            }
        }

        return prompt.toString();
    }
}