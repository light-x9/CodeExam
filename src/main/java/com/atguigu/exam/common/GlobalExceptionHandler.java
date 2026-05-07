package com.atguigu.exam.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 
 * 作用：拦截所有 Controller 抛出的异常，统一转成 Result 格式返回给前端
 * 
 * 核心注解：
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 * 表示这个类会拦截所有 Controller 的异常，并返回 JSON
 * 
 * 为什么需要它？
 * ┌─ 没有异常处理器时 ─────────────────────────────┐
 * │ Controller 抛 RuntimeException                 │
 * │   → Spring Boot 返回 HTTP 500                   │
 * │   → 响应体是 Spring 默认的错误 JSON（格式混乱）     │
 * │   → 前端拿不到我们定义的 code/message            │
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
     * 处理 RuntimeException 及其子类
     * 
     * 覆盖场景：
     * - 登录时用户名不存在 → throw new RuntimeException("用户名或密码错误")
     * - 登录时密码错误     → throw new RuntimeException("用户名或密码错误")
     * - 登录时非管理员     → throw new RuntimeException("无权限：仅管理员可登录后台")
     * - 其他任何业务异常   → throw new RuntimeException("具体错误信息")
     * 
     * @param e 被抛出的异常对象
     * @return 统一的错误 Result
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        // 记录完整的异常堆栈，方便排查问题
        log.error("业务异常：{}", e.getMessage(), e);
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
