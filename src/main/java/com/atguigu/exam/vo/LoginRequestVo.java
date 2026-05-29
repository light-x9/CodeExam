package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求 VO
 * 
 * account 字段同时支持：
 * - 学生：输入学号（如 20230001）
 * - 管理员：输入用户名（如 admin）
 * 
 * 后端自动匹配 username 或 student_no 两个字段
 */
@Data
@Schema(description = "用户登录请求参数")
public class LoginRequestVo {
    
    @Schema(description = "学号或用户名", 
            example = "20230001", 
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "学号/用户名不能为空")
    private String account;
    
    @Schema(description = "登录密码", 
            example = "123456", 
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密码不能为空")
    private String password;
}