package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改密码请求 VO
 * 
 * 前端只需要传旧密码和新密码，不需要传 userId——
 * 当前用户身份从 ThreadLocal（UserContext）中获取，防止越权修改他人密码。
 */
@Data
@Schema(description = "修改密码请求参数")
public class ChangePasswordVo {

    @Schema(description = "旧密码（用于验证身份）",
            example = "123456",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @Schema(description = "新密码",
            example = "newPassword123",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
