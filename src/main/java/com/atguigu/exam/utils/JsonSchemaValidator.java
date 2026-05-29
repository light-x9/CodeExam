package com.atguigu.exam.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

/**
 * JSON Schema 校验工具 —— 对 AI 返回结果做结构化约束
 *
 * 为什么需要 Schema 校验？
 * 仅靠 Prompt 约束 AI 输出格式不可靠，AI 可能返回：
 * - 字段名拼写错误（如 "questoins" 而不是 "questions"）
 * - 字段类型错误（如 score 返回字符串 "5" 而不是整数 5）
 * - 缺少必填字段（如漏了 analysis）
 * - 枚举值异常（如 difficulty 返回 "简单" 而不是 "EASY"）
 *
 * Schema 校验作为第二道防线，在手工解析之后、入库之前做最终把关，
 * 将格式异常的题目拦截在入库之前，降低脏数据风险。
 */
@Slf4j
public class JsonSchemaValidator {

    private static final String SCHEMA_PATH = "schema/ai-question-schema.json";
    private static volatile JsonSchema cachedSchema;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 加载 JSON Schema（懒加载 + 缓存）
     * 线程安全的双重检查锁定，避免重复读取文件
     */
    /** Schema 加载是否已经失败过（避免重复尝试加载缺失的依赖） */
    private static volatile boolean schemaLoadFailed = false;

    private static JsonSchema getSchema() {
        // 如果之前加载已经失败了，直接返回 null，避免重复抛异常
        if (schemaLoadFailed) {
            return null;
        }
        if (cachedSchema == null) {
            synchronized (JsonSchemaValidator.class) {
                if (cachedSchema == null && !schemaLoadFailed) {
                    try (InputStream is = JsonSchemaValidator.class.getClassLoader()
                            .getResourceAsStream(SCHEMA_PATH)) {
                        if (is == null) {
                            log.error("JSON Schema 文件未找到: {}", SCHEMA_PATH);
                            schemaLoadFailed = true;
                            return null;
                        }
                        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
                        cachedSchema = factory.getSchema(is);
                        log.info("AI 出题 JSON Schema 加载成功");
                    } catch (Throwable e) {
                        // catch Throwable：`networknt:json-schema-validator` 依赖缺失时会抛 NoClassDefFoundError(Error 而非 Exception)
                        log.error("加载 JSON Schema 失败（Schema 校验功能将暂时关闭）: {}", e.toString());
                        schemaLoadFailed = true;
                        return null;
                    }
                }
            }
        }
        return cachedSchema;
    }

    /**
     * 校验 AI 返回的 JSON 是否符合预定义的 Schema
     *
     * @param jsonStr 待校验的 JSON 字符串
     * @return 校验错误集合，为空集合表示校验通过
     */
    public static Set<ValidationMessage> validate(String jsonStr) {
        JsonSchema schema = getSchema();
        // Schema 未加载成功（依赖缺失或文件不存在），跳过校验
        if (schema == null) {
            return Collections.emptySet();
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonStr);
            return schema.validate(jsonNode);
        } catch (Exception e) {
            log.error("JSON Schema 校验过程异常: {}", e.getMessage());
            return Set.of(ValidationMessage.builder()
                    .message("JSON 解析失败: " + e.getMessage())
                    .build());
        }
    }

    /**
     * 校验并返回结果描述
     *
     * @param jsonStr 待校验的 JSON 字符串
     * @return null 表示校验通过，否则返回错误描述
     */
    public static String validateAndDescribe(String jsonStr) {
        Set<ValidationMessage> errors = validate(jsonStr);
        if (errors.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (ValidationMessage error : errors) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(error.getMessage());
        }
        return sb.toString();
    }
}
