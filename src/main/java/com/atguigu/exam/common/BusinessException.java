package com.atguigu.exam.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 业务异常 —— 用于在 Service 层抛出可预见的业务错误
 * 
 * ==================== 为什么需要这个类？ ====================
 * 
 * 之前：整个项目用 new RuntimeException("错误信息") 表示业务错误
 *   问题：
 *   1. RuntimeException 太宽泛——无法区分"业务异常"和"真正的程序BUG"
 *   2. 如果要返回不同的 HTTP 状态码（如 400 参数错误 vs 500 服务器错误），
 *      RuntimeException 没法携带 code
 *   3. 未来接入监控/告警时，需要区分"预期内的业务失败"和"需要告警的系统异常"
 * 
 * 现在：Service 层抛 BusinessException
 *   Controller → Service → throw BusinessException(400, "用户名已存在")
 *           → GlobalExceptionHandler 拦截 → Result.error(400, "用户名已存在")
 * 
 * 优势：
 * - 可以携带自定义业务状态码（如 400, 409 等）
 * - GlobalExceptionHandler 区分对待 BusinessException 和 RuntimeException
 * - RuntimeException 仍然表示真正的未知异常（500）
 * 
 * @author 智能学习平台 - 用户注册功能
 */
@Getter  // 只生成 getter，不生成 setter（异常信息不应被修改）
@Schema(description = "业务异常信息")
public class BusinessException extends RuntimeException {

    /**
     * 业务状态码
     * 如：400（参数错误）、409（冲突/重复）、403（无权限）
     * 默认 500 表示服务器内部错误
     */
    @Schema(description = "业务状态码", example = "409")
    private final Integer code;

    /**
     * 构造方法：自定义状态码 + 错误信息
     * 
     * @param code    业务状态码，如 409 表示"用户名已存在"冲突
     * @param message 面向用户的错误消息，如"用户名已存在，请更换"
     */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造方法：使用默认 500 状态码
     * 
     * @param message 错误消息
     */
    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }
}
