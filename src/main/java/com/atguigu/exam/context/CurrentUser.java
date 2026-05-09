package com.atguigu.exam.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户上下文实体 —— 贯穿整个请求生命周期的用户快照
 * 
 * ==================== 为什么需要这个类？ ====================
 * 
 * 之前：用户信息散落在 request.setAttribute("userId")、("username")、("role") 中
 * 问题：
 *   1. 每次获取都要强转：(Long) request.getAttribute("userId")
 *   2. key 是魔法字符串，写错不会编译报错，运行时才炸
 *   3. 一个请求周期内多次调用，每次都走 request.getAttribute（虽然不重但啰嗦）
 *   4. 无法 IDE 智能提示，重构困难
 *   5. AOP 里需要注入 HttpServletRequest 才能拿到，耦合了 Servlet API
 * 
 * 现在：一个 CurrentUser 对象贯穿整个请求链路
 * 优势：
 *   1. 类型安全：currentUser.getUserId() → IDE 自动补全
 *   2. 一次解析、处处可用
 *   3. AOP / Service 不再依赖 HttpServletRequest，解耦 Servlet API
 *   4. 未来扩展（如加部门、租户ID）只需加一个字段，所有调用方自动可用
 * 
 * ==================== 不可变性设计 ====================
 * 
 * CurrentUser 设计为"一旦创建就不修改"的不可变对象：
 * - 所有字段通过构造器注入，没有 Setter（用 @Data 但有 AllArgsConstructor）
 * - 避免某处不小心改了 userId 导致安全问题
 * - 如果需要"模拟"其他用户（如管理员代操作），新构造一个对象即可
 * 
 * @author 智能学习平台 - ThreadLocal 用户上下文优化
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUser {

    /** 用户ID —— 数据库主键 */
    private Long userId;

    /** 用户名 —— 登录账号 */
    private String username;

    /** 用户角色 —— ADMIN / TEACHER / STUDENT */
    private String role;

    /**
     * 快捷判断：是否为管理员
     * 省去各处写 "ADMIN".equals(user.getRole()) 的样板代码
     */
    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(this.role);
    }

    /**
     * 快捷判断：是否为教师
     */
    public boolean isTeacher() {
        return "TEACHER".equalsIgnoreCase(this.role);
    }

    /**
     * 检查当前用户是否拥有指定角色
     * 
     * @param requiredRole 要求的角色
     * @return true=拥有该角色
     */
    public boolean hasRole(String requiredRole) {
        return requiredRole != null && requiredRole.equalsIgnoreCase(this.role);
    }

    /**
     * 检查当前用户是否拥有指定角色列表中的任一角色
     * 
     * @param requiredRoles 允许的角色列表
     * @return true=满足任一角色
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
