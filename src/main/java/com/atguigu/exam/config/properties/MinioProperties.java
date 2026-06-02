package com.atguigu.exam.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 对象存储配置属性
 * 与 KimiApiProperties 保持一致的配置风格
 */
@Data
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /** MinIO 服务地址，如 http://<your-minio-host>:9000 */
    private String endpoint;

    /** 访问密钥 */
    private String accessKey;

    /** 私有密钥 */
    private String secretKey;

    /** 存储桶名称 */
    private String bucket;

    /** 文件访问的对外域名/Nginx 代理地址（若不配则直接用 endpoint） */
    private String externalEndpoint;

    /**
     * 获取文件访问的前缀 URL
     * 优先使用 externalEndpoint（如 Nginx 代理地址），否则用原始 endpoint
     */
    public String getAccessUrlPrefix() {
        String base = (externalEndpoint != null && !externalEndpoint.isBlank()) ? externalEndpoint : endpoint;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + bucket + "/";
    }
}
