package com.atguigu.exam.common;

import lombok.Getter;

/**
 * 统一错误码枚举
 *
 * 所有业务异常的错误码和消息在这里集中定义，取代之前在代码中硬编码的 400/409/500 魔数。
 * 前端可以通过 code 做差异化处理（如 401 → 跳转登录页，409 → 弹窗提示换用户名）。
 */
@Getter
public enum ErrorCode {

    // ==================== 通用 ====================
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数有误"),
    VALIDATION_ERROR(400, "参数校验失败"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统繁忙，请稍后再试"),

    // ==================== 认证相关 4xx ====================
    NOT_LOGIN(401, "未登录，请先登录"),
    TOKEN_INVALID(401, "Token无效或已过期，请重新登录"),
    TOKEN_EXPIRED(401, "Token已失效，请重新登录"),
    USERNAME_OR_PASSWORD_ERROR(401, "用户名或密码错误"),
    OLD_PASSWORD_ERROR(400, "旧密码错误"),
    PASSWORD_SAME_AS_OLD(400, "新密码不能与旧密码相同"),
    PERMISSION_DENIED(403, "权限不足"),
    USER_NOT_FOUND(404, "用户不存在"),

    // ==================== 注册相关 ====================
    PASSWORD_NOT_MATCH(400, "两次输入的密码不一致"),
    USERNAME_INVALID(400, "用户名不合法"),
    USERNAME_DUPLICATE(409, "用户名已被注册，请更换"),
    REGISTER_FAILED(500, "注册失败，请稍后重试"),

    // ==================== 题目相关 ====================
    QUESTION_NOT_FOUND(404, "题目不存在"),
    QUESTION_TITLE_DUPLICATE(409, "同类型下已存在同名题目"),
    QUESTION_REFERENCED(409, "题目正被试卷引用，无法删除"),

    // ==================== 分类相关 ====================
    CATEGORY_NOT_FOUND(404, "分类不存在"),
    CATEGORY_DUPLICATE(409, "同级分类下已存在同名分类"),
    CATEGORY_HAS_QUESTIONS(409, "该分类下存在题目，无法删除"),
    CATEGORY_IS_ROOT(400, "不能删除一级分类"),
    CATEGORY_HAS_CHILDREN(409, "该分类下有子分类，无法删除"),
    CATEGORY_HAS_VIDEOS(409, "该分类下有视频，无法删除"),
    CATEGORY_SELF_PARENT(400, "不能将自己设为父级分类"),
    PARENT_CATEGORY_NOT_FOUND(404, "父级分类不存在"),
    PARENT_CATEGORY_DISABLED(400, "父级分类已被禁用"),

    // ==================== 试卷相关 ====================
    PAPER_NOT_FOUND(404, "试卷不存在"),
    PAPER_CREATE_FAILED(500, "试卷创建失败"),
    PAPER_QUESTION_EMPTY(400, "试卷必须包含至少一道题目"),

    // ==================== 视频相关 ====================
    VIDEO_NOT_FOUND(404, "视频不存在"),
    VIDEO_NOT_PUBLISHED(400, "视频未发布或已下架"),
    VIDEO_FILE_EMPTY(400, "视频文件不能为空"),
    VIDEO_UPLOAD_FAILED(500, "视频上传失败"),
    VIDEO_AUDIT_ERROR(400, "只能审核待审核状态的视频"),
    VIDEO_REJECT_REASON_REQUIRED(400, "拒绝审核时必须填写拒绝原因"),
    VIDEO_OFFLINE_ERROR(400, "只能下架已发布的视频"),

    // ==================== Excel 导入相关 ====================
    EXCEL_FILE_EMPTY(400, "Excel文件为空"),
    EXCEL_FORMAT_ERROR(400, "文件格式错误，必须是 xls 或 xlsx 格式"),

    // ==================== AI 相关 ====================
    AI_GENERATE_FAILED(500, "AI生成题目失败"),
    AI_RESPONSE_EMPTY(500, "AI返回结果为空"),
    AI_RESPONSE_FORMAT_ERROR(500, "AI返回结果格式异常"),

    // ==================== 文件上传相关 ====================
    FILE_EMPTY(400, "上传文件不能为空"),
    FILE_EXTENSION_NOT_ALLOWED(400, "不支持的文件类型"),
    FILE_SIZE_EXCEEDED(400, "文件大小超出限制"),
    FILE_UPLOAD_FAILED(500, "文件上传失败，请稍后重试"),
    ;

    /** HTTP 业务状态码 */
    private final Integer code;

    /** 面向用户的消息 */
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
