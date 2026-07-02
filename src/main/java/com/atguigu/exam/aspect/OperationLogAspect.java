package com.atguigu.exam.aspect;

import com.atguigu.exam.annotation.OperationLog;
import com.atguigu.exam.context.CurrentUser;
import com.atguigu.exam.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 操作日志切面 —— AOP 统一日志记录 + ThreadLocal 用户上下文
 * 
 * ==================== ThreadLocal 优化版 ====================
 * 
 * 之前：通过 request.getAttribute("username") 获取操作用户
 *   问题：必须注入 HttpServletRequest，操作日志切面依赖了 Web 层对象
 * 
 * 现在：通过 UserContext.get() 获取 CurrentUser（用户名、ID、角色）
 *   优势：用户信息获取与 Servlet API 解耦，日志更丰富（可记录 userId 而不只是 username）
 * 
 * 注意：HttpServletRequest 仍保留用于获取请求 IP，这是合理的（IP 确实是 Web 层信息）
 * 
 * @author light
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    @Autowired
    private HttpServletRequest request;

    /**
     * 定义切点：所有贴了 @OperationLog 注解的方法
     */
    @Pointcut("@annotation(com.atguigu.exam.annotation.OperationLog)")
    public void logPointcut() {
        // 方法体为空 —— @Pointcut 只需要注解，不需要方法体
    }

    /**
     * 后置返回通知：在方法正常返回后记录操作日志
     * 
     * @param joinPoint    被拦截的方法
     * @param operationLog 方法上的 @OperationLog 注解
     * @param result       方法的返回值
     */
    @AfterReturning(pointcut = "logPointcut() && @annotation(operationLog)", returning = "result")
    public void logOperation(JoinPoint joinPoint, OperationLog operationLog, Object result) {
        // ======== 第1步：获取方法信息 ========
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();

        // ======== 第2步：从 ThreadLocal 获取用户信息（★ 不再从 request.getAttribute 获取） ========
        CurrentUser currentUser = UserContext.get();
        String username = (currentUser != null) ? currentUser.getUsername() : "未知用户";
        Long userId = (currentUser != null) ? currentUser.getUserId() : null;

        // ======== 第3步：获取操作描述和方法参数 ========
        String operation = operationLog.value();
        Object[] args = joinPoint.getArgs();

        // ━━━━━━━━ 【学习日志】操作日志切面 ━━━━━━━━
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║  [5] OperationLogAspect.@AfterReturning() - 操作日志  ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║  线程：" + Thread.currentThread().getName() + "（依然是同一个线程！）");
        System.out.println("║  UserContext.get() = " + currentUser + "  ← Controller 返回后，数据还在！");
        System.out.println("║  操作描述：" + operation);
        System.out.println("║  ★ 注意：这里是 @AfterReturning，Controller 已返回，但 ThreadLocal 还有数据");
        System.out.println("║  ★ 只有 afterCompletion() 中的 remove() 才会清空它");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();

        // ======== 第4步：统一格式记录操作日志 ========
        // 这里打印到控制台，实际项目可以写到数据库（操作日志表）
        log.info("┌────────────────── 操作日志 ──────────────────");
        log.info("│ 操作用户：{} (ID={})", username, userId);
        log.info("│ 操作描述：{}", operation);
        log.info("│ 调用方法：{}", methodName);
        log.info("│ 方法参数：{}", Arrays.toString(args));
        log.info("│ 执行结果：成功");
        log.info("│ 请求 IP ：{}", request.getRemoteAddr());
        log.info("└──────────────────────────────────────────────");

        // ======== 扩展思考：实际项目还可以做什么？ ========
        // - 把日志写入数据库（操作日志表：操作人ID、操作人姓名、时间、IP、操作内容、参数）
        // - 异常操作告警（如短时间内大量删除操作 → 发钉钉/企微通知）
        // - 操作审计（谁、什么时候、改了什么数据）
        // - 操作回放（把参数记下来，后续可以复现问题）
    }
}
