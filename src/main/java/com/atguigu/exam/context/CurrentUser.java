package com.atguigu.exam.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户上下文实体 - 贯穿整个请求生命周期的用户快照
 * 
 * 不可变设计：一旦创建就不修改，防止中途被篡改
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUser {

    /** 用户ID - 数据库主键 */
    private Long userId;

    /** 用户名 - 登录账号 */
    private String username;

    /** 学号/工号 - 唯一身份标识 */
    private String studentNo;

    /** 真实姓名 */
    private String realName;

    /** 用户角色 - ADMIN / TEACHER / STUDENT */
    private String role;

    /** 快捷判断：是否为管理员 */
    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(this.role);
    }

    /** 快捷判断：是否为教师 */
    public boolean isTeacher() {
        return "TEACHER".equalsIgnoreCase(this.role);
    }

    /**
     * 检查当前用户是否拥有指定角色
     */
    public boolean hasRole(String requiredRole) {
        return requiredRole != null && requiredRole.equalsIgnoreCase(this.role);
    }

    /**
     * 检查当前用户是否拥有指定角色列表中的任一角色
     */
    public boolean hasAnyRole(String... requiredRoles) {
        if (requiredRoles == null || this.role == null) {
            return false;
        }
        for (String r : requiredRoles) {
            if (r.equalsIgnoreCase(this.role)) {
                return true;
            }
        }
        return false;
    }
}