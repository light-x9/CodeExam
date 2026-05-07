package com.atguigu.exam.aspect;

import com.atguigu.exam.annotation.RequireRole;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 权限校验切面 —— Level 4 的核心演示：AOP + 自定义注解
 * 
 * ==================== 面试高频：Spring AOP 的核心概念 ====================
 * 
 * ┌──────────────┬──────────────────────────────────────────────────────┐
 * │    概念       │                      解释                             │
 * ├──────────────┼──────────────────────────────────────────────────────┤
 * │ Aspect（切面）│ 切面 = 切点 + 通知。本类就是一个切面                    │
 * │ Pointcut（切点）│ "在哪里切" —— @annotation(RequireRole) 表示切所有     │
 * │              │   贴了 @RequireRole 注解的方法                          │
 * │ Advice（通知） │ "切进去干什么" —— @Around 环绕通知，方法执行前后都拦截  │
 * │ JoinPoint    │ "被拦截的具体方法" —— ProceedingJoinPoint 表示当前方法  │
 * │ （连接点）    │                                                       │
 * └──────────────┴──────────────────────────────────────────────────────┘
 * 
 * ==================== AOP 的执行流程 ====================
 * 
 *  用户请求
 *    ↓
 *  LoginInterceptor.preHandle()      ← Level 3：HTTP 层（有没有 Token？）
 *    ↓ (Token 有效，把 userId/role 放入 request.setAttribute)
 *  Controller 方法                     ← 准备执行
 *    ↓
 *  PermissionAspect.@Around()        ← Level 4：方法层（有没有权限？）
 *    ↓ (角色匹配)
 *  proceed() → 执行业务逻辑           ← 真正执行
 *    ↓
 *  返回结果给用户
 * 
 * ==================== 核心优势 ====================
 * 
 * 不用 AOP 的话，每个需要权限校验的方法里都要写：
 *   String role = (String) request.getAttribute("role");
 *   if (!"ADMIN".equals(role)) throw new RuntimeException("无权限");
 * 
 * 用了 AOP：一行 @RequireRole("ADMIN") 搞定，权限逻辑集中在切面里。
 * 
 * @author 智能学习平台 - Level 4 AOP切面编程
 */
@Slf4j
@Aspect     // ← 告诉 Spring："我是一个切面，请用我来拦截方法"
@Component  // ← 注入 Spring 容器
public class PermissionAspect {

    @Autowired
    private HttpServletRequest request;

    /**
     * 环绕通知：拦截所有贴了 @RequireRole 注解的方法
     * 
     * @Around 是最强大的通知类型，可以：
     *   1. 在方法执行前做检查
     *   2. 决定是否让方法执行（proceed）
     *   3. 在方法执行后修改返回值
     *   4. 捕获方法抛出的异常
     * 
     * @param joinPoint 被拦截的方法（连接点）
     * @param requireRole 方法上的 @RequireRole 注解
     * @return 方法的原始返回值
     */
    @Around("@annotation(requireRole)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequireRole requireRole) throws Throwable {
        // ======== 第1步：拿到被拦截方法的签名 ========
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();

        // ======== 第2步：从请求属性中获取当前用户信息 ========
        // 这些信息是 LoginInterceptor 在验证 Token 后放进去的
        String currentRole = (String) request.getAttribute("role");
        String username = (String) request.getAttribute("username");

        if (currentRole == null) {
            log.warn("权限校验失败：请求中无用户角色信息 - 方法：{}", methodName);
            throw new RuntimeException("未登录或登录已过期");
        }

        // ======== 第3步：比对角色 —— 当前用户角色是否在允许列表中 ========
        String[] requiredRoles = requireRole.value();
        boolean hasPermission = Arrays.asList(requiredRoles).contains(currentRole);

        if (!hasPermission) {
            log.warn("权限不足：用户 {} (角色={}) 尝试执行 {}，要求角色：{}",
                     username, currentRole, methodName, Arrays.toString(requiredRoles));
            throw new RuntimeException(
                String.format("权限不足：需要 %s 角色，当前为 %s",
                              Arrays.toString(requiredRoles), currentRole)
            );
        }

        // ======== 第4步：权限通过，执行原始方法 ========
        log.info("权限校验通过：用户 {} (角色={}) 执行 {}", username, currentRole, methodName);
        return joinPoint.proceed();  // ← 这行才真正执行 Controller 方法
    }
}
