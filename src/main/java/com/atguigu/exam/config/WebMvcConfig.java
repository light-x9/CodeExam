package com.atguigu.exam.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置 —— 注册拦截器
 * 
 * ==================== 为什么需要这个配置类？ ====================
 * 
 * 拦截器写好了不会自动生效，必须通过 WebMvcConfigurer 注册到 Spring MVC 中。
 * 
 * addPathPatterns("/api/**")   → 拦截哪些路径
 * excludePathPatterns(...)     → 白名单：哪些路径不拦截
 * 
 * 当前白名单策略：
 * - /api/user/login  —— 登录接口本身不能拦截，否则死循环
 * - /api/user/register —— 注册接口（预留）
 * - Swagger/Knife4j 文档 —— 方便开发调试
 * 
 * @author 智能学习平台 - Level 3 无状态认证
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;

    /**
     * 注册拦截器
     * 
     * ┌─ 拦截器链执行顺序 ─────────────────────────────────────┐
     * │                                                        │
     * │  请求 → LoginInterceptor.preHandle()                    │
     * │          ├─ true  → Controller → 返回结果              │
     * │          └─ false → 直接返回 401，不进入 Controller      │
     * │                                                        │
     * │  注意：登录接口 /api/user/login 在白名单中，不会被拦截   │
     * └────────────────────────────────────────────────────────┘
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                // 拦截所有 /api/** 的请求
                .addPathPatterns("/api/**")
                // 白名单：以下路径不需要登录
                .excludePathPatterns(
                        "/api/user/login",           // 登录接口
                        "/api/user/register",        // 注册接口（预留）
                        // Swagger / Knife4j 文档路径放行
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/v3/api-docs/**",
                        "/doc.html",
                        "/webjars/**"
                );
    }
}
