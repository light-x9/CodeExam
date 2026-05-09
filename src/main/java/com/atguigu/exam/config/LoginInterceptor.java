package com.atguigu.exam.config;

import com.atguigu.exam.context.CurrentUser;
import com.atguigu.exam.context.UserContext;
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
 * ==================== ThreadLocal 优化版 ====================
 * 
 * 之前：JWT 解析后通过 request.setAttribute 传递用户信息
 *   问题：Controller / Service / AOP 都需要注入 HttpServletRequest 才能获取，
 *        代码啰嗦，强依赖 Servlet API，无法在非 Web 层使用。
 * 
 * 现在：JWT 解析后构造 CurrentUser 对象 → 存入 UserContext（ThreadLocal）
 *   优势：整个请求链路（拦截器 → AOP → Controller → Service）都能通过
 *        UserContext.get() 直接获取，无需传参，无需注入 HttpServletRequest。
 * 
 * ==================== 拦截器执行流程（优化后） ====================
 * 
 * 请求 → preHandle()
 *           ├─ JWT 校验成功 → UserContext.set(currentUser) → true → Controller
 *           └─ JWT 校验失败 → 返回 401
 *        → postHandle()      ← Controller 执行后
 *        → afterCompletion() ← ★ UserContext.remove() 清理 ThreadLocal
 * 
 * @author 智能学习平台 - ThreadLocal 用户上下文优化
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
     * 4. 解析 Token 中的用户信息 → 构造 CurrentUser → 存入 UserContext（ThreadLocal）
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

        // ======== 第4步：解析 Token → 构造 CurrentUser → 存入 ThreadLocal ========
        // ★ 优化点：之前是 request.setAttribute("userId", ...)，
        //          现在是 UserContext.set(currentUser)，后续整个链路都能直接获取。
        try {
            Long userId = jwtUtil.getUserIdFromToken(token);
            String username = jwtUtil.getUsernameFromToken(token);
            String role = jwtUtil.getRoleFromToken(token);

            // 构造不可变的 CurrentUser 对象
            CurrentUser currentUser = CurrentUser.builder()
                    .userId(userId)
                    .username(username)
                    .role(role)
                    .build();

            // ━━━━━━━━ 【学习日志】set 之前检查 ━━━━━━━━
            System.out.println();
            System.out.println("╔══════════════════════════════════════════════════════╗");
            System.out.println("║  [1] LoginInterceptor.preHandle() - 请求入口          ║");
            System.out.println("╠══════════════════════════════════════════════════════╣");
            System.out.println("║  线程：" + Thread.currentThread().getName());
            System.out.println("║  URL ：" + request.getRequestURI());
            System.out.println("║  set前 UserContext.get() = " + UserContext.get() + "  ← 此时应该为 null");
            System.out.println("║  ★ 即将 set：userId=" + userId + ", username=" + username + ", role=" + role);
            System.out.println("╚══════════════════════════════════════════════════════╝");
            System.out.println();

            // 存入当前线程的 ThreadLocal
            UserContext.set(currentUser);

            // ━━━━━━━━ 【学习日志】set 之后验证 ━━━━━━━━
            System.out.println("╔══════════════════════════════════════════════════════╗");
            System.out.println("║  [1] LoginInterceptor.preHandle() - set 之后验证      ║");
            System.out.println("╠══════════════════════════════════════════════════════╣");
            System.out.println("║  线程：" + Thread.currentThread().getName());
            System.out.println("║  set后 UserContext.get() = " + UserContext.get() + "  ← 现在有数据了！");
            System.out.println("╚══════════════════════════════════════════════════════╝");
            System.out.println();

            log.debug("ThreadLocal 已绑定用户：userId={}, username={}, role={}, URL={}",
                      userId, username, role, request.getRequestURI());
        } catch (Exception e) {
            log.error("从 Token 提取用户信息失败", e);
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"Token解析失败\"}");
            return false;
        }

        return true; // 放行
    }

    /**
     * ★ 请求完成后回调 —— 清理 ThreadLocal
     * 
     * afterCompletion() 在以下情况下都会执行：
     * - Controller 正常返回
     * - Controller 抛出异常
     * - 任何中间环节抛出异常
     * 
     * 这是一个"兜底"的清理点，确保无论请求成功还是失败，
     * ThreadLocal 都会被清理，防止内存泄漏和线程复用时的脏数据问题。
     * 
     * 为什么不用 postHandle()？
     * - postHandle() 只在 Controller 正常返回时执行，抛异常时不执行
     * - afterCompletion() 无论成功/异常都会执行（类似 finally），是最可靠的清理点
     */
    @Override
    public void afterCompletion(HttpServletRequest request, 
                                HttpServletResponse response, 
                                Object handler, Exception ex) {
        // ━━━━━━━━ 【学习日志】remove 之前 ━━━━━━━━
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║  [6] LoginInterceptor.afterCompletion() - 请求结束     ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║  线程：" + Thread.currentThread().getName());
        System.out.println("║  remove前 UserContext.get() = " + UserContext.get() + "  ← 请求处理完，数据还在");
        System.out.println("║  ★ 即将执行 UserContext.remove() ...");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();

        // ★ 关键：必须在 afterCompletion 中清理 ThreadLocal
        // 无论请求成功还是失败，都要移除当前线程的用户信息
        UserContext.remove();

        // ━━━━━━━━ 【学习日志】remove 之后验证 ━━━━━━━━
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║  [6] LoginInterceptor.afterCompletion() - remove 之后  ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║  线程：" + Thread.currentThread().getName());
        System.out.println("║  remove后 UserContext.get() = " + UserContext.get() + "  ← 数据已清空！线程归还池");
        System.out.println("║  ★ 如果这里不是 null，下个请求就会拿到脏数据！");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();
    }
}
