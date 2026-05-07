package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.User;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.service.UserService;
import com.atguigu.exam.vo.LoginRequestVo;
import com.atguigu.exam.vo.LoginResponseVo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户Service实现类
 * 实现用户相关的业务逻辑
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    /**
     * 管理员登录
     * 流程：1.查用户 → 2.校验角色必须是ADMIN → 3.BCrypt加密比对密码 → 4.组装返回数据
     */
    @Override
    public LoginResponseVo login(LoginRequestVo loginRequestVo) {
        // ========== 第1步：根据用户名查询用户 ==========
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", loginRequestVo.getUsername());
        User user = this.getOne(queryWrapper);

        if (user == null) {
            log.warn("登录失败：用户名不存在 - {}", loginRequestVo.getUsername());
            throw new RuntimeException("用户名或密码错误");
        }

        // ========== 第2步：校验角色 —— 只有管理员才能登录后台 ==========
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            log.warn("登录失败：非管理员用户尝试登录后台 - {} (role={})", 
                     loginRequestVo.getUsername(), user.getRole());
            throw new RuntimeException("无权限：仅管理员可登录后台");
        }

        // ========== 第3步：BCrypt 加密比对密码 ==========
        // Level 1 用的是：password.equals(user.getPassword()) —— 明文比对，不安全
        // Level 2 升级为：passwordEncoder.matches(明文, 密文) —— 加密比对
        // matches() 内部：把输入的明文用同样的盐值加密一次 → 比对结果
        if (!passwordEncoder.matches(loginRequestVo.getPassword(), user.getPassword())) {
            log.warn("登录失败：密码错误 - {}", loginRequestVo.getUsername());
            throw new RuntimeException("用户名或密码错误");
        }

        // ========== 第4步：组装返回数据 ==========
        LoginResponseVo responseVo = new LoginResponseVo();
        responseVo.setUserId(user.getId());
        responseVo.setUsername(user.getUsername());
        responseVo.setRealName(user.getRealName());
        responseVo.setRole(user.getRole());
        responseVo.setToken(null);

        log.info("管理员登录成功：{} ({})", user.getUsername(), user.getRealName());
        return responseVo;
    }

} 