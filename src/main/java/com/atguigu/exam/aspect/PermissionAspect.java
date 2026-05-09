package com.atguigu.exam.aspect;

import com.atguigu.exam.annotation.RequireRole;
import com.atguigu.exam.context.CurrentUser;
import com.atguigu.exam.context.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 权限校验切面 —— AOP + 自定义注解 + ThreadLocal 用户上下文
 * 
 * ==================== ThreadLocal 优化版 ====================
 * 
 * 之前：通过 request.getAttribute("role") 获取用户角色
 *   问题：必须注入 HttpServletRequest，强依赖 Servlet API
 * 
 * 现在：通过 UserContext.get() 直接获取 CurrentUser 对象
 *   优势：不依赖 Servlet API，纯 POJO 操作，类型安全
 * 
 * ==================== AOP 的执行流程 ====================
 * 
 *  用户请求
 *    ↓
 *  LoginInterceptor.preHandle()      ← JWT 校验 → UserContext.set(currentUser)
 *    ↓
 *  PermissionAspect.@Around()        ← UserContext.get() → 角色比对
 *    ↓ (角色匹配)
 *  proceed() → 执行业务逻辑
 *    ↓
 *  返回结果给用户
 * 
 * ==================== 核心优势 ====================
 * 
 * 不用 AOP 的话，每个需要权限校验的方法里都要写：
 *   CurrentUser user = UserContext.get();
 *   if (!user.isAdmin()) throw new RuntimeException("无权限");
 * 
 * 用了 AOP：一行 @RequireRole("ADMIN") 搞定，权限逻辑集中在切面里。
 * 
 * @author 智能学习平台 - ThreadLocal 用户上下文优化
 */
@Slf4j
@Aspect     // ← 告诉 Spring："我是一个切面，请用我来拦截方法"
@Component  // ← 注入 Spring 容器
public class PermissionAspect {

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

        // ======== 第2步：从 ThreadLocal 中获取当前用户信息（★ 不再依赖 HttpServletRequest） ========
        CurrentUser currentUser = UserContext.get();

        if (currentUser == null) {
            log.warn("权限校验失败：ThreadLocal 中无用户信息 - 方法：{}", methodName);
            throw new RuntimeException("未登录或登录已过期");
        }

        // ======== 第3步：比对角色 —— 当前用户角色是否在允许列表中 ========
        // ★ 优化：使用 CurrentUser.hasAnyRole() 替代手动 contains 判断
        String[] requiredRoles = requireRole.value();

        if (!currentUser.hasAnyRole(requiredRoles)) {
            log.warn("权限不足：用户 {} (角色={}) 尝试执行 {}，要求角色：{}",
                     currentUser.getUsername(), currentUser.getRole(), methodName, Arrays.toString(requiredRoles));
            throw new RuntimeException(
                String.format("权限不足：需要 %s 角色，当前为 %s",
                              Arrays.toString(requiredRoles), currentUser.getRole())
            );
        }

        // ======== 第4步：权限通过，执行原始方法 ========
        log.info("权限校验通过：用户 {} (角色={}) 执行 {}", 
                 currentUser.getUsername(), currentUser.getRole(), methodName);
        return joinPoint.proceed();  // ← 这行才真正执行 Controller 方法
    }
}
