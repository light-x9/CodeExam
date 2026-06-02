package com.atguigu.exam.config;

import com.atguigu.exam.context.CurrentUser;
import com.atguigu.exam.context.UserContext;
import com.atguigu.exam.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
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
 * @author light
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * StringRedisTemplate —— 操作 Redis 的 String 类型数据
     * 
     * 为什么用 StringRedisTemplate 而不是 RedisTemplate？
     * - 黑名单只需要存 key-value 字符串（token → "1"）
     * - StringRedisTemplate 天然用 String 序列化，key 在 Redis 里可读
     * - 不需要额外的 JSON 序列化开销
     */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * Redis 黑名单开关
     * 
     * 从 application.yml 读取 jwt.blacklist.enabled 配置：
     * - false（默认）：跳过 Redis 黑名单校验，不连接 Redis，适合开发环境
     * - true：启用 Redis 黑名单，退出登录后 Token 立即失效
     */
    @Value("${jwt.blacklist.enabled:false}")
    private boolean blacklistEnabled;

    /**
     * Redis 黑名单 key 前缀
     * 完整 key 格式：logout:token:eyJhbGciOiJIUzI1NiJ9...
     */
    private static final String LOGOUT_TOKEN_PREFIX = "logout:token:";

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

        // ======== 第3步：验证 Token（JWT 签名 + 过期时间）========
        if (!jwtUtil.validateToken(token)) {
            log.warn("Token 无效或已过期，请求被拦截 - URL: {}", request.getRequestURI());
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"Token无效或已过期，请重新登录\"}");
            return false;
        }

        // ======== 第4步：检查 Redis 黑名单（JWT + Redis 双重校验）========
        // ★ JWT 本身是"签发后无法撤销"的，所以需要 Redis 黑名单来主动失效
        // 用户退出登录后，Token 被加入 Redis 黑名单，即使 JWT 签名有效也会被拒绝
        if (isTokenInBlacklist(token)) {
            log.warn("Token 命中 Redis 黑名单（已退出登录），请求被拦截 - URL: {}", request.getRequestURI());
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"Token已失效，请重新登录\"}");
            return false;
        }

        // ======== 第5步：解析 Token → 构造 CurrentUser → 存入 ThreadLocal ========
        // ★ 优化点：之前是 request.setAttribute("userId", ...)，
        //          现在是 UserContext.set(currentUser)，后续整个链路都能直接获取。
        try {
            Long userId = jwtUtil.getUserIdFromToken(token);
            String username = jwtUtil.getUsernameFromToken(token);
            String role = jwtUtil.getRoleFromToken(token);
            String studentNo = jwtUtil.getStudentNoFromToken(token);
            String realName = jwtUtil.getRealNameFromToken(token);

            // 构造不可变的 CurrentUser 对象
            CurrentUser currentUser = CurrentUser.builder()
                    .userId(userId)
                    .username(username)
                    .studentNo(studentNo)
                    .realName(realName)
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

    /**
     * 检查 Token 是否在 Redis 黑名单中
     * 
     * ==================== 为什么需要黑名单？ ====================
     * 
     * JWT 的一个固有问题：Token 签发后无法主动撤销。
     * 即使调用了 logout，JWT 本身仍然有效——因为它是"自包含"的，
     * 服务器没有存它的状态。
     * 
     * 解决方案：Redis 黑名单
     * - 退出登录时，把 Token 存入 Redis（key = logout:token:xxx）
     * - 后续请求先查 Redis：如果 key 存在 → Token 已被撤销 → 拒绝
     * - Redis key 设置过期时间 = JWT 剩余有效时间 → 到期自动清理
     * 
     * ==================== 性能考量 ====================
     * 
     * 每个请求多一次 Redis 查询，但：
     * - Redis 是内存数据库，单次 GET 只需 ~0.1ms
     * - 黑名单中的 Token 是少数（只有主动退出的用户）
     * - 大部分请求 Redis 返回 null（不存在），非常快
     * 
     * @param token JWT Token 字符串
     * @return true=在黑名单中，false=不在
     */
    private boolean isTokenInBlacklist(String token) {
        // ★ 黑名单开关关闭 → 直接跳过，不连接 Redis，不等待 timeout
        if (!blacklistEnabled) {
            return false;
        }
        // Redis key 格式：logout:token:eyJhbGciOiJIUzI1NiJ9...
        String redisKey = LOGOUT_TOKEN_PREFIX + token;
        // hasKey 返回 true 表示 key 存在 → token 在黑名单中
        Boolean hasKey = stringRedisTemplate.hasKey(redisKey);
        return Boolean.TRUE.equals(hasKey);
    }
}
