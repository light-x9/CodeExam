package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求 VO - 前端提交的注册表单数据
 * 
 * ==================== 学号校验规则 ====================
 * 
 * 1. 不能为空（@NotBlank）
 * 2. 长度 6~20（@Size）
 * 3. 纯数字（@Pattern）
 * 4. 全局唯一（Service 层校验，因为需要查数据库）
 */
@Data
@Schema(description = "用户注册请求参数")
public class RegisterRequestVo {

    /**
     * 用户名 - 登录账号，全局唯一
     */
    @Schema(description = "用户名（登录账号），长度 4~20，全局唯一",
            example = "zhangsan",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 4,
            maxLength = 20)
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度必须在 4~20 之间")
    private String username;

    /**
     * 学号 - 真实考试系统必须有的字段
     * 
     * 设计原因：
     * - 学号是唯一身份标识，姓名可能重复但学号不会
     * - 成绩、考试记录都以学号为准
     * - 方便与学校管理系统对接
     */
    @Schema(description = "学号/工号，纯数字，长度 6~20，全局唯一",
            example = "20230001",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 6,
            maxLength = 20)
    @NotBlank(message = "学号不能为空")
    @Size(min = 6, max = 20, message = "学号长度必须在 6~20 之间")
    @Pattern(regexp = "^[0-9]+$", message = "学号必须是纯数字")
    private String studentNo;

    /**
     * 密码 - 登录密码，BCrypt 加密存储
     */
    @Schema(description = "登录密码，长度至少 6 位",
            example = "123456",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 6)
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码长度至少为 6 位")
    private String password;

    /**
     * 确认密码 - 必须与 password 一致
     */
    @Schema(description = "确认密码，必须与密码一致",
            example = "123456",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;

    /**
     * 真实姓名（可选）
     */
    @Schema(description = "真实姓名（可选）",
            example = "张三")
    private String realName;
}