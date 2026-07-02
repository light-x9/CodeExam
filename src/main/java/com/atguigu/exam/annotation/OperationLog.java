package com.atguigu.exam.annotation;

import java.lang.annotation.*;

/**
 * 自定义注解 —— 标记需要记录操作日志的方法
 * 
 * ==================== AOP 的经典应用场景：操作日志 ====================
 * 
 * 假设有 50 个 Controller 方法都需要记录操作日志，你会怎么做？
 * 
 * 方案 A（不用 AOP）：
 *   每个方法里都写：
 *     log.info("用户 {} 执行了 {} 操作，参数：{}", username, action, params);
 *   50 个方法 = 50 段重复代码，哪天要改日志格式？改 50 处。
 * 
 * 方案 B（用 AOP）：
 *   在方法上贴 @OperationLog("删除题目")
 *   AOP 切面统一处理，只写一次日志逻辑，改一次全改。
 * 
 * ==================== AOP 的威力 ====================
 * 
 * AOP 最核心的价值就是：
 *   "把相同的代码从 50 个地方，收敛到 1 个地方"
 * 
 * @author light
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /**
     * 操作描述，比如 "删除题目"、"创建试卷"、"修改公告"
     */
    String value() default "";
}
