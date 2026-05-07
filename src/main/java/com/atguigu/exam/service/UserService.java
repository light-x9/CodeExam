package com.atguigu.exam.service;

import com.atguigu.exam.entity.User;
import com.atguigu.exam.vo.LoginRequestVo;
import com.atguigu.exam.vo.LoginResponseVo;
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

} 