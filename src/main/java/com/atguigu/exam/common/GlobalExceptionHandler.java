package com.atguigu.exam.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 * 将 Controller 抛出的异常统一转成 Result 格式返回。
 *
 * 异常处理优先级（从具体到宽泛）：
 *   1. BusinessException     → 业务异常，使用 ErrorCode 中定义的状态码
 *   2. MethodArgumentNotValidException → @Valid 参数校验失败，固定 400
 *   3. RuntimeException      → 未预期的运行时异常，固定 500，隐藏内部细节
 *   4. Exception             → 兜底，固定 500
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理 BusinessException —— 预期内的业务异常
     *
     * 根据 ErrorCode 中的状态码返回不同的 HTTP 语义：
     * - 400 参数错误 / 业务规则校验失败
     * - 401 未登录 / Token 无效
     * - 403 权限不足
     * - 404 资源不存在
     * - 409 资源冲突（如用户名重复）
     * - 500 服务内部错误（如 AI 调用失败）
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        if (e.getCode() >= 500) {
            log.error("业务异常(code={}): {}", e.getCode(), e.getMessage(), e);
        } else {
            log.warn("业务异常(code={}): {}", e.getCode(), e.getMessage());
        }
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理 @Valid 参数校验失败
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getFieldError().getDefaultMessage();
        log.warn("参数校验失败：{}", errorMessage);
        return Result.error(ErrorCode.VALIDATION_ERROR.getCode(), errorMessage);
    }

    /**
     * 处理未预期的 RuntimeException
     * 不直接暴露 e.getMessage()，避免泄露内部实现细节
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.error("未预期的运行时异常 - 类型={}, 消息={}", e.getClass().getName(), e.getMessage(), e);
        return Result.error(ErrorCode.INTERNAL_ERROR.getCode(),
                            ErrorCode.INTERNAL_ERROR.getMessage());
    }

    /**
     * 兜底：所有未被上面捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常 - 类型={}, 消息={}", e.getClass().getName(), e.getMessage(), e);
        return Result.error(ErrorCode.INTERNAL_ERROR.getCode(),
                            "系统内部异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
    }
}
