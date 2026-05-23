package com.atguigu.exam.common;

import lombok.Getter;

/**
 * 业务异常 —— Service 层抛出，GlobalExceptionHandler 统一拦截
 *
 * 改造后：
 * - 必须传入 ErrorCode 枚举，不再使用裸 Integer 硬编码
 * - 支持覆盖默认消息（少数需要动态拼接消息的场景）
 * - 所有业务异常都应使用此类，杜绝直接 throw new RuntimeException()
 *
 * 用法：
 *   throw new BusinessException(ErrorCode.USERNAME_DUPLICATE);
 *   throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND, "id=123 的题目不存在");
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 错误码（枚举，包含 code 和 message） */
    private final ErrorCode errorCode;

    /** 最终返回给前端的 HTTP 状态码 */
    private final Integer code;

    /**
     * 使用 ErrorCode 中定义的默认消息
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.code = errorCode.getCode();
    }

    /**
     * 覆盖 ErrorCode 中的默认消息（用于需要动态拼接错误信息的场景）
     *
     * 例如：throw new BusinessException(ErrorCode.QUESTION_REFERENCED,
     *           "id=5 的题目被试卷《Java基础》引用");
     */
    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
        this.code = errorCode.getCode();
    }
}
