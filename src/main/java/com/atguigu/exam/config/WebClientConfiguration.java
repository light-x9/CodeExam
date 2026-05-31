package com.atguigu.exam.config;

import com.atguigu.exam.config.properties.KimiApiProperties;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * WebClient 配置类 — 用于调用 Kimi API
 * <p>
 * 配置了连接超时（10s）和读取超时（60s），
 * 避免 AI 批卷时因网络问题导致请求卡死。
 */
@Configuration
@EnableConfigurationProperties(KimiApiProperties.class)
public class WebClientConfiguration {

    @Autowired
    private KimiApiProperties kimiApiProperties;

    /**
     * 连接超时（毫秒），默认 10 秒
     */
    @Value("${kimi.api.timeout.connect:10000}")
    private int connectTimeout;

    /**
     * 读取超时（毫秒），默认 60 秒
     * AI 批卷可能需要较长时间，所以设置得比较宽松
     */
    @Value("${kimi.api.timeout.read:60000}")
    private int readTimeout;

    /**
     * 配置 WebClient Bean，用于调用 Kimi API
     */
    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeout / 1000))
                        .addHandlerLast(new WriteTimeoutHandler(10)));

        return WebClient.builder()
                .baseUrl(kimiApiProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + kimiApiProperties.getApiKey())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }
}