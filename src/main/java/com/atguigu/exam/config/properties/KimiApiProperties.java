package com.atguigu.exam.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * projectName: exam_system_server_online
 *
 * @author light
 * description: 存储kimi交互的四个核心参数
 * kimi api baseUrl
 *          apikey
 *          model
 *          temperature
 */
@Data
@ConfigurationProperties(prefix = "kimi.api")
public class KimiApiProperties {

    /**
     * Kimi API 服务地址
     */
    private String baseUrl;

    /**
     * API 调用密钥（用于身份验证）
     */
    private String apiKey;

    /**
     * 使用的模型版本（如 kimi-plus 等）
     */
    private String model;

    /**
     * 温度参数（控制输出随机性，取值范围 0~2）
     */
    private Double temperature;

    /**
     * 最大生成长度（控制输出长度）
     */
    private Integer maxTokens;

}