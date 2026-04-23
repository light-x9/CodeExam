package com.atguigu.exam.config;

import com.atguigu.exam.config.properties.KimiApiProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * projectName: exam_system_server_online
 *
 * @author: 赵伟风
 * description: webClient 加入核心容器的配置类
 */
@Configuration
@EnableConfigurationProperties(KimiApiProperties.class)
public class WebClientConfiguration {

    @Autowired
    private KimiApiProperties kimiApiProperties;

    /**
     * 配置 WebClient Bean，用于调用 Kimi API
     * 自动注入 KimiApiProperties 中的配置参数
     */
    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                // 设置基础 URL，从 KimiApiProperties 读取
                .baseUrl(kimiApiProperties.getBaseUrl())
                // 设置默认请求头：Content-Type 为 application/json
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // 设置认证请求头：Authorization Bearer + API Key
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + kimiApiProperties.getApiKey())
                // 构建 WebClient 实例
                .build();
    }
}