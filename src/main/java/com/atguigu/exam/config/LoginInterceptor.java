package com.atguigu.exam.config;

import com.atguigu.exam.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器 —— 保护后台接口，验证 JWT Token
 * 
 * ==================== 面试高频：拦截器(Interceptor) vs 过滤器(Filter) ====================
 * 
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │                        对比表格                                     │
 * ├──────────────┬─────────────────────┬────────────────────────────────┤
 * │    维度       │   Filter（过滤器）   │   Interceptor（拦截器）         │
 * ├──────────────┼─────────────────────┼────────────────────────────────┤
 * │ 所属规范      │ Servlet 规范         │ Spring MVC 规范                │
 * │ 能访问的上下文 │ 只能拿 request/response │ 可以拿 Spring Bean、Handler  │
 * │ 执行时机      │ 进入 Servlet 前       │ 进入 Controller 前后           │
 * │ 使用场景      │ 编码设置、安全过滤    │ 登录鉴权、日志记录、权限校验    │
 * └──────────────┴─────────────────────┴────────────────────────────────┘
 * 
 * 我们这里选 Interceptor，因为：
 * 1. 需要注入 Spring Bean（JwtUtil）
 * 2. 需要知道请求要访问哪个 Controller 方法（可以按需放行）
 * 
 * ==================== 拦截器执行流程 ====================
 * 
 * 请求 → preHandle() → true → Controller → postHandle() → afterCompletion()
 *                ↘ false → 直接返回 401，不再走 Controller
 * 
 * @author 智能学习平台 - Level 3 无状态认证
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 前置处理：在 Controller 方法执行前调用
     * 
     * 做了什么？
     * 1. 检查请求头是否带了 Authorization
     * 2. 提取 Bearer Token
     * 3. 验证 Token 有效
     * 4. 把用户信息写入请求属性，方便后续使用
     * 
     * @return true=放行，false=拦截（返回401）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, 
                             HttpServletResponse response, 
                             Object handler) throws Exception {

        // ======== 第1步：获取 Authorization 请求头 ========
        String authHeader = request.getHeader(jwtUtil.getHeader());

        // ======== 第2步：提取 Token ========
        String token = jwtUtil.extractToken(authHeader);
        if (token == null) {
            log.warn("未携带 Token，请求被拦截 - URL: {}", request.getRequestURI());
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"未登录，请先登录\"}");
            return false;
        }

        // ======== 第3步：验证 Token ========
        if (!jwtUtil.validateToken(token)) {
            log.warn("Token 无效或已过期，请求被拦截 - URL: {}", request.getRequestURI());
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"Token无效或已过期，请重新登录\"}");
            return false;
        }

        // ======== 第4步：提取用户信息，放入请求属性 ========
        // 后续 Controller 可以通过 request.getAttribute("userId") 获取当前用户
        try {
            Long userId = jwtUtil.getUserIdFromToken(token);
            String username = jwtUtil.getUsernameFromToken(token);
            String role = jwtUtil.getRoleFromToken(token);

            request.setAttribute("userId", userId);
            request.setAttribute("username", username);
            request.setAttribute("role", role);

            log.debug("Token 验证通过：用户={}，角色={}，URL={}", username, role, request.getRequestURI());
        } catch (Exception e) {
            log.error("从 Token 提取用户信息失败", e);
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"Token解析失败\"}");
            return false;
        }

        return true; // 放行
    }
}
