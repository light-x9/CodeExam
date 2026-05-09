package com.atguigu.exam.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置 —— 注册拦截器
 * 
 * ==================== Level 3：为什么需要这个配置类？ ====================
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
 * ==================== Level 4：Interceptor vs AOP ====================
 * 
 * 现在系统有两层防护：
 * 
 * 第1层：LoginInterceptor（HTTP 层）
 *   时机：请求进入 Controller 之前
 *   做啥：检查有没有 JWT Token → 没有就返回 401
 *   范围：/api/** 全部拦截（登录接口除外）
 * 
 * 第2层：PermissionAspect + @RequireRole（方法层）
 *   时机：Controller 方法执行之前
 *   做啥：检查 Token 里的角色是否满足方法要求的角色 → 不满足抛异常
 *   范围：只拦截贴了 @RequireRole 的方法
 * 
 * ┌─────────────────────────────────────────────────────────────┐
 * │                    请求处理全流程（ThreadLocal 优化后）        │
 * │                                                             │
 * │  用户请求                                                    │
 * │    ↓                                                        │
 * │  LoginInterceptor.preHandle()     ← JWT 校验 → UserContext.set() │
 * │    ├─ 无 Token → 401                                       │
 * │    └─ 有 Token → CurrentUser 存入 ThreadLocal               │
 * │    ↓                                                        │
 * │  PermissionAspect.@Around()        ← UserContext.get() → 角色校验 │
 * │    ├─ 角色不匹配 → 抛异常                                  │
 * │    └─ 角色匹配 → proceed()                                 │
 * │    ↓                                                        │
 * │  Controller 方法执行业务逻辑          ← UserContext.get() 获取用户 │
 * │    ↓                                                        │
 * │  OperationLogAspect.@AfterReturning() ← UserContext.get() 记录日志 │
 * │    ↓                                                        │
 * │  返回结果给前端                                              │
 * │    ↓                                                        │
 * │  LoginInterceptor.afterCompletion() ← ★ UserContext.remove() 清理 │
 * └─────────────────────────────────────────────────────────────┘
 * 
 * @author 智能学习平台 - Level 3/4 拦截器与AOP
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
                        // ======== 认证相关（必须放行，否则无法登录/退出） ========
                        "/api/user/login",           // 登录接口
                        "/api/user/logout",          // 退出登录接口（无需登录态，幂等操作）
                        "/api/user/register",        // 注册接口（预留）

                        // ======== 首页公开数据（无需登录就能看） ========
                        "/api/banners/**",           // 轮播图（含图片资源）
                        "/api/notices/latest",       // 最新公告
                        "/api/questions/popular",    // 热门题目
                        "/api/stats/overview",       // 首页统计概览

                        // Swagger / Knife4j 文档路径放行
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/v3/api-docs/**",
                        "/doc.html",
                        "/webjars/**"
                );
    }
}
