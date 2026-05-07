package com.atguigu.exam.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 安全配置类 — 注册 BCrypt 密码加密器
 * 
 * BCrypt 是什么？
 * ┌─────────────────────────────────────────────────────┐
 * │ BCrypt 是一个专门为密码存储设计的哈希算法             │
 * │                                                     │
 * │ 它的三个核心特点：                                    │
 * │ 1. 自带"盐值"（Salt）—— 同一个密码两次加密结果不同    │
 * │ 2. 故意很慢 —— 暴力破解成本高                        │
 * │ 3. 加密结果自带盐值 —— 不用额外存盐值字段             │
 * │                                                     │
 * │ 示例：                                               │
 * │ admin123 加密后 →                                   │
 * │ $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy │
 * │  ↑↑  ↑↑  ↑──────────────────盐值──────────────────↑ ↑──────密文──────↑ │
 * │ 算法 强度                                             │
 * └─────────────────────────────────────────────────────┘
 * 
 * 为什么不引入整个 spring-security？
 * 整个 Spring Security 框架很重，会强制要求所有接口都登录。
 * 我们只需要它的加密模块，所以只引 spring-security-crypto。
 */
@Configuration
public class SecurityConfig {

    /**
     * 注册 BCryptPasswordEncoder 到 Spring 容器
     * 
     * 之后在任何地方都可以 @Autowired 注入使用
     * 
     * @return BCryptPasswordEncoder 实例
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        // 参数 10 表示加密强度（cost factor），范围 4~31
        // 10 是业界默认值，加密一次约 0.1 秒
        // 越大越安全但也越慢
        return new BCryptPasswordEncoder(10);
    }
}
