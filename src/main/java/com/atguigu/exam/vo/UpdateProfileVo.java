package com.atguigu.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 修改个人信息请求 VO
 */
@Data
@Schema(description = "修改个人信息请求参数")
public class UpdateProfileVo {

    @Schema(description = "真实姓名", example = "张三")
    private String realName;
}
