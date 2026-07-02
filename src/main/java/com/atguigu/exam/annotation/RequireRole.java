package com.atguigu.exam.annotation;

import java.lang.annotation.*;

/**
 * 自定义注解 —— 标记方法需要的角色
 * 
 * ==================== 面试高频：注解(Annotation)是怎么工作的？ ====================
 * 
 * 注解本身只是一段"元数据"（metadata），贴在类/方法/字段上，它自己不会执行。
 * 真正干活的是"注解处理器" —— 在 Spring AOP 中，就是 @Aspect 切面类。
 * 
 * 流程：@RequireRole("ADMIN") 贴在方法上
 *        → Spring AOP 扫描到它
 *        → PermissionAspect 的 @Around 拦截方法执行
 *        → 从 JWT 里拿到当前用户角色
 *        → 比对 → 不匹配就抛异常
 * 
 * ==================== 元注解说明 ====================
 * 
 * @Target(ElementType.METHOD) —— 只能贴在方法上
 * @Retention(RetentionPolicy.RUNTIME) —— 运行时保留，AOP 才能在运行时读到
 * @Documented —— 生成 JavaDoc 时会包含这个注解
 * 
 * ==================== 和 Inteceptor 的区别 ====================
 * 
 * ┌────────────────┬──────────────────────┬──────────────────────────────┐
 * │    维度         │   LoginInterceptor   │   @RequireRole + AOP         │
 * ├────────────────┼──────────────────────┼──────────────────────────────┤
 * │ 拦截粒度        │ URL 级别（/api/**）   │ 方法级别（单个 Java 方法）      │
 * │ 能否拿参数       │ 只能拿 request/response│ 能拿到方法的参数、返回值      │
 * │ 判断依据         │ 有无 Token            │ Token 里的角色 + 方法要求的角色 │
 * │ 适用场景         │ "有没有登录"           │ "有没有权限做这件事"          │
 * └────────────────┴──────────────────────┴──────────────────────────────┘
 * 
 * 简单说：Interceptor 管"进不进得去"，AOP 管"能不能做"。
 * 
 * @author light
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /**
     * 要求的角色
     * 默认 ADMIN，也可以指定 TEACHER、STUDENT 等
     * 
     * 用法示例：
     * @RequireRole("ADMIN")           → 只有管理员能调
     * @RequireRole("TEACHER")         → 只有教师能调
     * @RequireRole({"ADMIN", "TEACHER"}) → 管理员或教师都能调
     */
    String[] value() default {"ADMIN"};
}
