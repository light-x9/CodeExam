package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 开始考试请求 VO
 * 
 * ==================== 重要改动 ====================
 * 
 * 之前：需要前端传 paperId + studentName（手动输入姓名）
 * 现在：只需要传 paperId，学生信息从 JWT 自动获取
 * 
 * 为什么这样设计？
 * - 真实考试系统不会让学生"输入姓名"，身份由登录系统确定
 * - JWT Token 里已经有 userId、username、studentNo、realName
 * - 前端只需选择试卷，后端自动从当前用户上下文获取身份
 */
@Data
@Schema(description = "开始考试请求参数")
public class StartExamVo {

    @Schema(description = "试卷ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer paperId;

    // studentName 字段已移除 —— 不再由前端传入，改为从 JWT 自动获取
}