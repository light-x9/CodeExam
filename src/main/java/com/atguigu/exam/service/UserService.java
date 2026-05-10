package com.atguigu.exam.service;

import com.atguigu.exam.entity.User;
import com.atguigu.exam.vo.ChangePasswordVo;
import com.atguigu.exam.vo.LoginRequestVo;
import com.atguigu.exam.vo.LoginResponseVo;
import com.atguigu.exam.vo.RegisterRequestVo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户Service接口
 * 定义用户相关的业务方法
 */
public interface UserService extends IService<User> {

    /**
     * 用户登录
     * @param loginRequestVo 登录请求参数（用户名、密码）
     * @return 登录成功返回用户信息，失败抛异常
     */
    LoginResponseVo login(LoginRequestVo loginRequestVo);

    /**
     * 修改密码
     * 
     * @param userId      当前登录用户ID（从 ThreadLocal 获取，不由前端传递）
     * @param requestVo   旧密码 + 新密码
     */
    void changePassword(Long userId, ChangePasswordVo requestVo);

    /**
     * 用户注册
     * 
     * ==================== 注册流程 ====================
     * 
     * 1. 校验两次密码是否一致
     * 2. 校验用户名是否重复
     * 3. BCrypt 加密密码
     * 4. 设置默认角色 = STUDENT、默认状态 = ACTIVE
     * 5. 保存到数据库
     * 
     * ==================== 安全性 ====================
     * 
     * - 密码使用 BCrypt 加密，数据库不存明文
     * - 默认分配给最低权限角色（STUDENT），遵循最小权限原则
     * - 用户名唯一性由 Service 层校验 + 数据库唯一索引双层保障
     * 
     * @param requestVo 注册请求参数（用户名、密码、确认密码）
     * @throws com.atguigu.exam.common.BusinessException 校验失败时抛出
     */
    void register(RegisterRequestVo requestVo);

} 