package com.atguigu.exam.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 
 * 作用：拦截所有 Controller 抛出的异常，统一转成 Result 格式返回给前端
 * 
 * ==================== 异常处理优先级 ====================
 * 
 * Spring 按异常类型从"最具体"到"最宽泛"的顺序匹配处理器：
 *   1. BusinessException          → 业务异常（可带自定义状态码）
 *   2. RuntimeException          → 运行时异常（兜底，状态码 500）
 *   3. Exception                 → 所有异常（最终兜底，不暴露内部信息）
 * 
 * 核心注解：
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 * 表示这个类会拦截所有 Controller 的异常，并返回 JSON
 * 
 * 为什么需要它？
 * ┌─ 没有异常处理器时 ─────────────────────────────┐
 * │ Controller 抛 RuntimeException                
 * │   → Spring Boot 返回 HTTP 500                  │
 * │   → 响应体是 Spring 默认的错误 JSON（格式混乱）  │
 * │   → 前端拿不到我们定义的 code/message           │
 * └───────────────────────────────────────────────┘
 * 
 * ┌─ 有了异常处理器后 ─────────────────────────────┐
 * │ Controller 抛 RuntimeException                 │
 * │   → @ExceptionHandler 拦截到                    │
 * │   → 转成 Result.error("错误信息")                │
 * │   → 返回 HTTP 200 + 统一 JSON 格式               │
 * │   → 前端正常解析 code/message                   │
 * └───────────────────────────────────────────────┘
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * ★ 处理 BusinessException —— 预期内的业务异常
     * 
     * 覆盖场景：
     * - 注册时用户名已存在 → throw new BusinessException(409, "用户名已存在")
     * - 注册时两次密码不一致 → throw new BusinessException(400, "两次密码不一致")
     * - 任何可预见的业务校验失败
     * 
     * 与 RuntimeException 的区别：
     * - BusinessException 可携带自定义状态码（如 409 冲突）
     * - 日志级别为 warn（预期内），不打印堆栈（减少日志噪音）
     * 
     * @param e 业务异常对象
     * @return 携带自定义状态码的错误 Result
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        // warn 级别：业务异常是预期内的，不需要打印堆栈
        log.warn("业务异常：code={}, message={}", e.getCode(), e.getMessage());
        // 使用 BusinessException 自带的状态码
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理 @Valid 参数校验失败异常
     * 
     * 触发场景：
     * - 注册时 username 为空 → @NotBlank 校验失败
     * - 注册时 username 长度不在 4~20 → @Size 校验失败
     * - 注册时 password 长度 < 6 → @Size 校验失败
     * 
     * MethodArgumentNotValidException 里包含了所有校验失败的字段和错误消息，
     * 这里只取第一条错误消息返回给前端（避免一次性展示太多错误吓到用户）。
     * 
     * @param e 参数校验异常
     * @return 400 + 第一条校验错误消息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        // 获取第一条校验失败的错误消息
        // getBindingResult() → 所有校验结果
        // getFieldError() → 第一个字段校验错误
        // getDefaultMessage() → 你在 @NotBlank(message="...") 里写的消息
        String errorMessage = e.getBindingResult().getFieldError().getDefaultMessage();
        log.warn("参数校验失败：{}", errorMessage);
        // 400 Bad Request —— 前端提交的参数格式不对
        return Result.error(400, errorMessage);
    }

    /**
     * 处理 RuntimeException 及其子类（不包括 BusinessException）
     * 
     * 覆盖场景：
     * - 登录时用户名不存在 → throw new RuntimeException("用户名或密码错误")
     * - 登录时密码错误     → throw new RuntimeException("用户名或密码错误")
     * - 登录时非管理员     → throw new RuntimeException("无权限：仅管理员可登录后台")
     * - 其他未预期的运行时异常
     * 
     * @param e 被抛出的异常对象
     * @return 统一的错误 Result（状态码 500）
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        // 记录完整的异常堆栈，方便排查问题
        log.error("运行时异常：{}", e.getMessage(), e);
        // 把异常消息包装成统一格式返回
        return Result.error(e.getMessage());
    }

    /**
     * 兜底处理：捕获所有上面没处理的异常
     * 防止出现"系统未知错误"这种模糊提示
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("系统繁忙，请稍后再试");
    }
}
