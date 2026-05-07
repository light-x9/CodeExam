package com.atguigu.exam.aspect;

import com.atguigu.exam.annotation.OperationLog;
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
 * 操作日志切面 —— 演示 AOP 的另一经典场景：统一日志记录
 * 
 * ==================== @AfterReturning vs @Around ====================
 * 
 * ┌─────────────────┬────────────────────────────────────────────────────┐
 * │   通知类型       │                     说明                           │
 * ├─────────────────┼────────────────────────────────────────────────────┤
 * │ @Before          │ 方法执行前，拿不到返回值                           │
 * │ @AfterReturning  │ 方法成功返回后，能拿到返回值（本类用的就是这个）     │
 * │ @AfterThrowing   │ 方法抛异常后，适合做异常记录                       │
 * │ @After           │ 无论成功/异常都执行（类似 finally）                 │
 * │ @Around          │ 最强大，包围整个方法执行（PermissionAspect用的）    │
 * └─────────────────┴────────────────────────────────────────────────────┘
 * 
 * 这里选 @AfterReturning 的理由：
 * - 操作日志只需要记录"谁、在什么时候、做了什么、结果如何"
 * - 不需要阻止方法执行（和权限校验不同）
 * - 不影响正常业务逻辑
 * 
 * ==================== @Pointcut 的作用 ====================
 * 
 * @Pointcut 给切点表达式起个名字，方便复用。
 * 比如这里定义了 logPointcut()，后面的 @AfterReturning 直接引用它，
 * 不用每处都写一遍长长的切点表达式。
 * 
 * @author 智能学习平台 - Level 4 AOP切面编程
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    @Autowired
    private HttpServletRequest request;

    /**
     * 定义切点：所有贴了 @OperationLog 注解的方法
     * 
     * 切点表达式 "@annotation(com.atguigu.exam.annotation.OperationLog)"
     * 意思是：拦截所有被 @OperationLog 标记的方法
     */
    @Pointcut("@annotation(com.atguigu.exam.annotation.OperationLog)")
    public void logPointcut() {
        // 方法体为空 —— @Pointcut 只需要注解，不需要方法体
    }

    /**
     * 后置返回通知：在方法正常返回后记录操作日志
     * 
     * 注意：pointcut 中必须用 @annotation(operationLog) 绑定 operationLog 参数
     * 否则 Spring AOP 会报 "formal unbound in pointcut" 错误
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

        // ======== 第2步：获取用户信息 ========
        String username = (String) request.getAttribute("username");
        if (username == null) {
            username = "未知用户";
        }

        // ======== 第3步：获取操作描述和方法参数 ========
        String operation = operationLog.value();
        Object[] args = joinPoint.getArgs();

        // ======== 第4步：统一格式记录操作日志 ========
        // 这里打印到控制台，实际项目可以写到数据库（操作日志表）
        log.info("┌────────────────── 操作日志 ──────────────────");
        log.info("│ 操作用户：{}", username);
        log.info("│ 操作描述：{}", operation);
        log.info("│ 调用方法：{}", methodName);
        log.info("│ 方法参数：{}", Arrays.toString(args));
        log.info("│ 执行结果：成功");
        log.info("│ 请求 IP ：{}", request.getRemoteAddr());
        log.info("└──────────────────────────────────────────────");

        // ======== 扩展思考：实际项目还可以做什么？ ========
        // - 把日志写入数据库（操作日志表：操作人、时间、IP、操作内容、参数）
        // - 异常操作告警（如短时间内大量删除操作 → 发钉钉/企微通知）
        // - 操作审计（谁、什么时候、改了什么数据）
        // - 操作回放（把参数记下来，后续可以复现问题）
    }
}
