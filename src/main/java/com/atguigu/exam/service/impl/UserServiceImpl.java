package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.User;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.service.UserService;
import com.atguigu.exam.utils.JwtUtil;
import com.atguigu.exam.vo.ChangePasswordVo;
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

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 管理员登录
     * 
     * Level 1：数据库查用户 → 明文比对密码
     * Level 2：BCrypt 加密比对密码
     * Level 3：JWT 无状态认证 → 登录成功生成 Token，后续请求靠 Token 鉴权
     * 
     * 流程：1.查用户 → 2.校验角色 → 3.BCrypt比对密码 → 4.生成JWT → 5.组装返回
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

        // ========== 第4步：生成 JWT Token（Level 3 新增）==========
        // 之前 Level 1/2 只是 setToken(null)，现在真正生成 Token
        // JWT 里放了 userId、username、role 三样信息
        // 后续请求只要带上这个 Token，服务器就能知道是谁、什么角色
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        // ========== 第5步：组装返回数据 ==========
        LoginResponseVo responseVo = new LoginResponseVo();
        responseVo.setUserId(user.getId());
        responseVo.setUsername(user.getUsername());
        responseVo.setRealName(user.getRealName());
        responseVo.setRole(user.getRole());
        responseVo.setToken(token);  // Level 3：返回真正的 JWT Token，不再是 null

        log.info("管理员登录成功：{} ({})，Token已生成", user.getUsername(), user.getRealName());
        return responseVo;
    }

    /**
     * 修改密码
     * 
     * ==================== 业务流程 ====================
     * 
     * 1. 根据 userId 查询用户（userId 来自 ThreadLocal，不由前端传递）
     * 2. BCrypt 校验 oldPassword 是否正确
     * 3. BCrypt 加密 newPassword
     * 4. 更新数据库
     * 
     * ==================== 安全设计 ====================
     * 
     * - userId 从 ThreadLocal（UserContext）获取，不信任前端传递
     *   → 防止用户 A 修改用户 B 的密码（越权漏洞）
     * 
     * - 旧密码校验失败不暴露具体原因（与登录逻辑一致）
     *   → 防止攻击者通过错误信息嗅探密码
     * 
     * - 新旧密码不能相同
     *   → 防止"改了个寂寞"
     * 
     * - 密码修改成功后，Controller 层负责将当前 Token 加入 Redis 黑名单
     *   → Service 层只关心业务逻辑，不耦合 HTTP 层（单一职责）
     * 
     * @param userId    当前登录用户ID
     * @param requestVo 旧密码 + 新密码
     */
    @Override
    public void changePassword(Long userId, ChangePasswordVo requestVo) {
        // ========== 第1步：根据用户ID查询用户 ==========
        // 注意：这里用 userId 查，而不是用 username 查
        // userId 来自 ThreadLocal（UserContext），是登录时 JWT 里解析出来的，可信
        User user = this.getById(userId);
        if (user == null) {
            log.error("修改密码失败：用户不存在 - userId={}", userId);
            throw new RuntimeException("用户不存在");
        }

        // ========== 第2步：校验旧密码 ==========
        // BCrypt.matches(明文, 密文) 内部流程：
        //   1. 从密文中提取盐值（$2a$10$盐值$密文）
        //   2. 用同样的盐值加密输入的明文
        //   3. 比对两个密文是否相同
        if (!passwordEncoder.matches(requestVo.getOldPassword(), user.getPassword())) {
            log.warn("修改密码失败：旧密码错误 - userId={}", userId);
            throw new RuntimeException("旧密码错误");
        }

        // ========== 第3步：校验新旧密码不能相同 ==========
        if (requestVo.getOldPassword().equals(requestVo.getNewPassword())) {
            log.warn("修改密码失败：新旧密码相同 - userId={}", userId);
            throw new RuntimeException("新密码不能与旧密码相同");
        }

        // ========== 第4步：BCrypt 加密新密码 ==========
        // encode() 内部：随机生成盐值 → 用盐值加密明文 → 返回 $2a$10$盐值$密文
        // 每次 encode 生成的密文都不一样（因为盐值随机），但都能被 matches() 正确校验
        String encodedNewPassword = passwordEncoder.encode(requestVo.getNewPassword());

        // ========== 第5步：更新数据库 ==========
        user.setPassword(encodedNewPassword);
        this.updateById(user);

        log.info("密码修改成功：userId={}, username={}", userId, user.getUsername());
    }

} 