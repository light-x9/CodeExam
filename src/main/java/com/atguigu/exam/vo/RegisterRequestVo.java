package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求 VO —— 前端提交的注册表单数据
 * 
 * ==================== 校验规则 ====================
 * 
 * 1. username：不能为空，长度 4~20
 * 2. password：不能为空，长度至少 6 位
 * 3. confirmPassword：不能为空，在 Service 层校验与 password 的一致性
 *    → 为什么不在 VO 层校验两次密码一致？
 *    → 因为 @NotBlank 只能保证非空，跨字段校验（password vs confirmPassword）
 *       写在 Service 层更清晰，也能给出更友好的错误提示
 * 
 * ==================== Jakarta Validation 注解说明 ====================
 * 
 * @NotBlank：字符串不能为 null 且去除首尾空格后长度 > 0
 * @Size(min=4, max=20)：字符串长度必须在 [4, 20] 闭区间内
 *   → 注意：String 用 @Size，集合也适用；数字范围用 @Min / @Max
 * 
 * @author 智能学习平台 - 用户注册功能
 */
@Data
@Schema(description = "用户注册请求参数")
public class RegisterRequestVo {

    /**
     * 用户名 —— 登录账号，全局唯一
     * 
     * 约束：
     * - 不能为空（@NotBlank）
     * - 长度 4~20 字符（@Size）
     * - 不能与已有用户重复（Service 层校验）
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
     * 密码 —— 登录密码，BCrypt 加密存储
     * 
     * 约束：
     * - 不能为空（@NotBlank）
     * - 长度至少 6 位（@Size）
     * - 数据库中存储的是 BCrypt 加密后的密文，不是明文
     */
    @Schema(description = "登录密码，长度至少 6 位",
            example = "123456",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 6)
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码长度至少为 6 位")
    private String password;

    /**
     * 确认密码 —— 必须与 password 一致
     * 
     * 校验逻辑在 Service 层：
     *   if (!password.equals(confirmPassword)) → throw BusinessException
     * 
     * 只做 @NotBlank，跨字段一致性校验放在 Service 层
     */
    @Schema(description = "确认密码，必须与密码一致",
            example = "123456",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
