package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.User;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.service.UserService;
import com.atguigu.exam.vo.LoginRequestVo;
import com.atguigu.exam.vo.LoginResponseVo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户Service实现类
 * 实现用户相关的业务逻辑
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * 管理员登录
     * 流程：1.查用户 → 2.校验角色必须是ADMIN → 3.比对密码 → 4.组装返回数据
     */
    @Override
    public LoginResponseVo login(LoginRequestVo loginRequestVo) {
        // ========== 第1步：根据用户名查询用户 ==========
        // QueryWrapper 是 MyBatis Plus 提供的条件构造器
        // 相当于 SQL: SELECT * FROM users WHERE username = ?
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", loginRequestVo.getUsername());
        User user = this.getOne(queryWrapper);

        // 用户不存在 → 抛异常
        if (user == null) {
            log.warn("登录失败：用户名不存在 - {}", loginRequestVo.getUsername());
            throw new RuntimeException("用户名或密码错误");
        }

        // ========== 第2步：校验角色 —— 只有管理员才能登录后台 ==========
        // 认证（Authentication）：你是谁 → 用户名+密码
        // 授权（Authorization）：你能干嘛 → 角色判断
        // 这两步要分开！先认证，再授权
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            log.warn("登录失败：非管理员用户尝试登录后台 - {} (role={})", 
                     loginRequestVo.getUsername(), user.getRole());
            throw new RuntimeException("无权限：仅管理员可登录后台");
        }

        // ========== 第3步：比对密码 ==========
        // 注意：Level 1 是明文比对，Level 2 会升级为 BCrypt 加密比对
        if (!loginRequestVo.getPassword().equals(user.getPassword())) {
            log.warn("登录失败：密码错误 - {}", loginRequestVo.getUsername());
            throw new RuntimeException("用户名或密码错误");
        }

        // ========== 第4步：组装返回数据 ==========
        LoginResponseVo responseVo = new LoginResponseVo();
        responseVo.setUserId(user.getId());
        responseVo.setUsername(user.getUsername());
        responseVo.setRealName(user.getRealName());
        responseVo.setRole(user.getRole());
        // token 暂时留空，Level 3 会填充 JWT Token
        responseVo.setToken(null);

        log.info("管理员登录成功：{} ({})", user.getUsername(), user.getRealName());
        return responseVo;
    }

} 