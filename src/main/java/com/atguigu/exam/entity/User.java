package com.atguigu.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体类 - 系统用户信息模型
 * 
 * ==================== 真实考试系统为什么需要学号？ ====================
 * 
 * 1. 姓名不能作为唯一标识：
 *    - "张三"可能对应多个学生
 *    - 改名/入学后姓名变化导致历史记录无法追溯
 * 
 * 2. 学号必须唯一：
 *    - 每个学生在学校有且仅有一个学号
 *    - 学号是学生在系统内的"身份证号"
 *    - 成绩只认学号，不认姓名
 * 
 * 3. 数据库设计：
 *    - username（登录账号）唯一 → 用于登录
 *    - student_no（学号）唯一 → 用于成绩管理、与学校系统对接
 *    - real_name（真实姓名）→ 展示用，不唯一
 */
@Data
@TableName("users")
@Schema(description = "用户信息")
public class User extends BaseEntity {
    
    @Schema(description = "用户名，用于登录", 
            example = "admin")
    private String username;
    
    @Schema(description = "用户密码", 
            example = "******")
    private String password;
    
    @Schema(description = "学号/工号，全局唯一", 
            example = "20230001")
    @TableField("student_no")
    private String studentNo;
    
    @Schema(description = "用户真实姓名", 
            example = "张三")
    @TableField("real_name")
    private String realName;
    
    @Schema(description = "用户角色", 
            example = "ADMIN", 
            allowableValues = {"ADMIN", "TEACHER", "STUDENT"})
    private String role;
    
    @Schema(description = "用户状态", 
            example = "ACTIVE", 
            allowableValues = {"ACTIVE", "INACTIVE"})
    private String status;
}