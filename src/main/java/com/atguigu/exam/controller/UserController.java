package com.atguigu.exam.controller;


import com.atguigu.exam.common.Result;
import com.atguigu.exam.context.CurrentUser;
import com.atguigu.exam.context.UserContext;
import com.atguigu.exam.service.UserService;
import com.atguigu.exam.utils.JwtUtil;
import com.atguigu.exam.vo.ChangePasswordVo;
import com.atguigu.exam.vo.LoginRequestVo;
import com.atguigu.exam.vo.LoginResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;


/**
 * 用户控制器 - 处理用户认证和权限管理相关的HTTP请求
 * 
 * ==================== ThreadLocal 使用示例 ====================
 * 
 * 在本 Controller 中，演示了两种获取当前用户的方式：
 * 
 * 方式一（推荐）：UserContext.get()
 *   CurrentUser user = UserContext.get();
 *   → 任何地方都能用，不需要注入 HttpServletRequest，不依赖 Servlet API
 * 
 * 方式二：UserContext.require()
 *   CurrentUser user = UserContext.require();
 *   → 确保返回非 null，如果未登录直接抛异常，适合"必须登录"的场景
 * 
 * 快捷方法：
 *   Long userId = UserContext.getUserId();
 *   String username = UserContext.getUsername();
 *   → 只取单个字段时更方便
 */
@Slf4j
@RestController  // REST控制器，返回JSON数据
@RequestMapping("/api/user")  // 用户API路径前缀
@CrossOrigin(origins = "*")  // 允许跨域访问
@Tag(name = "用户管理", description = "用户相关操作，包括登录认证、权限验证等功能")  // Swagger API分组
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * StringRedisTemplate —— 用于 Redis 黑名单操作
     */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * Redis 黑名单 key 前缀（与 LoginInterceptor 保持一致）
     */
    private static final String LOGOUT_TOKEN_PREFIX = "logout:token:";

    /**
     * 用户登录
     * 流程：Controller接收参数 → 调用Service处理业务 → 包装成Result返回
     * @param loginRequestVo 登录请求
     * @return 登录结果
     */
    @PostMapping("/login")  // 处理POST请求
    @Operation(summary = "用户登录", description = "用户通过用户名和密码进行登录验证，返回用户信息和token")  // API描述
    public Result<LoginResponseVo> login(@RequestBody LoginRequestVo loginRequestVo) {
        // 调用 Service 层处理登录逻辑
        LoginResponseVo loginResponseVo = userService.login(loginRequestVo);
        // 用 Result 包装返回
        return Result.success(loginResponseVo);
    }
    
    /**
     * 获取当前登录用户信息
     * 
     * ==================== ThreadLocal 使用示例 ====================
     * 
     * 之前需要写成：
     *   public Result<User> getCurrentUser(HttpServletRequest request) {
     *       Long userId = (Long) request.getAttribute("userId");
     *       String username = (String) request.getAttribute("username");
     *       // 强转 + 魔法字符串，又丑又容易出错
     *   }
     * 
     * 现在只需一行：
     *   CurrentUser currentUser = UserContext.get();
     * 
     * @return 当前登录用户信息
     */
    @GetMapping("/current")
    @Operation(summary = "获取当前用户信息", description = "从 ThreadLocal 中获取当前登录用户信息，无需手动传 userId")
    public Result<CurrentUser> getCurrentUser() {
        // ★ 一行代码获取当前用户，不再需要注入 HttpServletRequest
        CurrentUser currentUser = UserContext.get();
        
        if (currentUser == null) {
            return Result.error("未登录");
        }
        
        log.info("当前用户：id={}, username={}, role={}", 
                 currentUser.getUserId(), currentUser.getUsername(), currentUser.getRole());
        
        return Result.success(currentUser);
    }
    
    /**
     * 检查用户权限
     * @param userId 用户ID
     * @return 权限检查结果
     */
    @GetMapping("/check-admin/{userId}")  // 处理GET请求
    @Operation(summary = "检查管理员权限", description = "验证指定用户是否具有管理员权限")  // API描述
    public Result<Boolean> checkAdmin(
            @Parameter(description = "用户ID") @PathVariable Long userId) {

        return Result.success(true);
    }

    /**
     * 用户退出登录 —— JWT + Redis 黑名单方案
     * 
     * ==================== 实现思路 ====================
     * 
     * JWT 本身无法主动失效（签发后就是有效的），所以需要一个"黑名单"：
     * 
     * 1. 从请求头中提取当前 Token
     * 2. 计算 Token 距离过期还有多久（剩余有效时间）
     * 3. 把 Token 存入 Redis 黑名单，key = logout:token:{token}
     * 4. 设置 Redis key 的过期时间 = Token 剩余有效时间
     *    → Token 过期后，Redis 自动删除这条黑名单记录
     *    → 不会在 Redis 里留下垃圾数据
     * 
     * ==================== 安全性说明 ====================
     * 
     * - 即使攻击者拿到了已退出的 Token，也会被 LoginInterceptor 拦截
     * - Redis 黑名单 key 有 TTL，到期自动清理，不会无限增长
     * - Token 过期后自动从黑名单消失，复用同样的 Token 也不可能
     * 
     * @param request HTTP 请求（用于获取 Authorization 头）
     * @return 退出结果
     */
    @PostMapping("/logout")
    @Operation(summary = "用户退出登录", description = "将当前 Token 加入 Redis 黑名单，实现主动失效")
    public Result<String> logout(HttpServletRequest request) {
        // ======== 第1步：从请求头提取 Token ========
        String authHeader = request.getHeader(jwtUtil.getHeader());
        String token = jwtUtil.extractToken(authHeader);

        if (token == null) {
            // 没有 Token 也算退出成功（幂等性）
            log.info("退出登录：未携带 Token，无需处理");
            return Result.success("已退出登录");
        }

        // ======== 第2步：计算 Token 剩余有效时间 ========
        long remainingTime = jwtUtil.getTokenRemainingTime(token);

        if (remainingTime <= 0) {
            // Token 已经过期了，不需要加入黑名单（拦截器会直接拦掉过期 Token）
            log.info("退出登录：Token 已过期，无需加入黑名单");
            return Result.success("已退出登录");
        }

        // ======== 第3步：将 Token 加入 Redis 黑名单 ========
        // key:   logout:token:eyJhbGciOiJIUzI1NiJ9...
        // value: "1"（占位符，只要有 key 存在就表示被拉黑）
        // TTL:   Token 剩余有效时间（毫秒）
        String redisKey = LOGOUT_TOKEN_PREFIX + token;
        stringRedisTemplate.opsForValue().set(
                redisKey,
                "1",                              // value 随意，只要有值就表示黑名单
                remainingTime,
                TimeUnit.MILLISECONDS
        );

        log.info("退出登录成功：Token 已加入 Redis 黑名单，{}ms 后自动过期", remainingTime);
        return Result.success("已退出登录");
    }

    /**
     * 修改密码 —— 登录用户修改自己的密码
     * 
     * ==================== 完整流程 ====================
     * 
     * ┌─ LoginInterceptor.preHandle()（自动执行）────────────┐
     * │  ① 校验 JWT Token                                  │
     * │  ② 检查 Redis 黑名单                               │
     * │  ③ 解析 Token → CurrentUser → UserContext.set()    │
     * └────────────────────────────────────────────────────┘
     *   ↓
     * ┌─ changePassword() ──────────────────────────────────┐
     * │  ④ 从 UserContext 获取 userId（不信任前端）          │
     * │  ⑤ 从请求头提取 Token（用于后续拉黑）                │
     * │  ⑥ 调用 Service：验旧密码 → 加密新密码 → 存数据库    │
     * │  ⑦ 成功：将当前 Token 加入 Redis 黑名单             │
     * │     → 修改密码后必须重新登录                        │
     * └────────────────────────────────────────────────────┘
     * 
     * ==================== 安全设计要点 ====================
     * 
     * 1. userId 来自 ThreadLocal（UserContext），不由前端传递
     *    → 防止 A 用户修改 B 用户的密码
     * 
     * 2. 修改密码后，当前 Token 立即拉黑
     *    → 防止攻击者用旧 Token 继续操作
     *    → 用户必须用新密码重新登录
     * 
     * 3. 接口不走白名单（经过 LoginInterceptor）
     *    → 确保 UserContext 可用
     *    → 确保 Token 有效才能改密码
     * 
     * @param request   HTTP 请求（获取 Authorization 头，用于拉黑 Token）
     * @param requestVo 旧密码 + 新密码
     * @return 操作结果
     */
    @PostMapping("/changePassword")
    @Operation(summary = "修改密码", description = "登录用户修改自己的密码，修改后当前 Token 失效，需要重新登录")
    public Result<String> changePassword(HttpServletRequest request,
                                          @RequestBody ChangePasswordVo requestVo) {
        // ======== 第1步：从 ThreadLocal 获取当前用户（不由前端传递！）========
        CurrentUser currentUser = UserContext.require();
        Long userId = currentUser.getUserId();

        // ======== 第2步：调用 Service 层修改密码 ========
        // 异常会被 GlobalExceptionHandler 捕获，统一返回错误响应
        userService.changePassword(userId, requestVo);

        // ======== 第3步：将当前 Token 加入 Redis 黑名单 ========
        // 密码都改了，旧 Token 当然不能再用了——否则攻击者拿到旧 Token 还能操作
        String authHeader = request.getHeader(jwtUtil.getHeader());
        String token = jwtUtil.extractToken(authHeader);

        if (token != null) {
            long remainingTime = jwtUtil.getTokenRemainingTime(token);
            if (remainingTime > 0) {
                String redisKey = LOGOUT_TOKEN_PREFIX + token;
                stringRedisTemplate.opsForValue().set(
                        redisKey, "1", remainingTime, TimeUnit.MILLISECONDS
                );
                log.info("密码修改后 Token 已拉黑：userId={}, {}ms 后自动过期", userId, remainingTime);
            }
        }

        log.info("密码修改成功：userId={}, 当前 Token 已失效", userId);
        return Result.success("密码修改成功，请重新登录");
    }
} 