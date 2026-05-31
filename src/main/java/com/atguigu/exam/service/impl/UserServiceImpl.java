package com.atguigu.exam.service.impl;

import com.atguigu.exam.common.BusinessException;
import com.atguigu.exam.common.ErrorCode;
import com.atguigu.exam.entity.User;
import com.atguigu.exam.mapper.UserMapper;
import com.atguigu.exam.service.UserService;
import com.atguigu.exam.utils.JwtUtil;
import com.atguigu.exam.vo.ChangePasswordVo;
import com.atguigu.exam.vo.LoginRequestVo;
import com.atguigu.exam.vo.LoginResponseVo;
import com.atguigu.exam.vo.RegisterRequestVo;
import com.atguigu.exam.vo.UpdateProfileVo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户Service实现类
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 登录：查用户 → BCrypt比对密码 → 生成JWT → 组装返回
     */
    @Override
    public LoginResponseVo login(LoginRequestVo loginRequestVo) {
        // account 字段同时支持学号和用户名登录
        String account = loginRequestVo.getAccount();
        
        // 先按学号查（学生登录）
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("student_no", account);
        User user = this.getOne(queryWrapper);
        
        // 学号没找到，再按用户名查（管理员/教师登录）
        if (user == null) {
            queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("username", account);
            user = this.getOne(queryWrapper);
        }

        if (user == null) {
            log.warn("登录失败：账号不存在 - {}", account);
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }

        if (!passwordEncoder.matches(loginRequestVo.getPassword(), user.getPassword())) {
            log.warn("登录失败：密码错误 - {}", loginRequestVo.getAccount());
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 生成 JWT Token（现在包含学号）
        String token = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getStudentNo(), user.getRealName(), user.getRole());

        LoginResponseVo responseVo = new LoginResponseVo();
        responseVo.setUserId(user.getId());
        responseVo.setUsername(user.getUsername());
        responseVo.setStudentNo(user.getStudentNo());
        responseVo.setRealName(user.getRealName());
        responseVo.setRole(user.getRole());
        responseVo.setToken(token);

        log.info("用户登录成功：{} (学号={}, role={})", user.getUsername(), user.getStudentNo(), user.getRole());
        return responseVo;
    }

    /**
     * 修改密码
     */
    @Override
    public LoginResponseVo changePassword(Long userId, ChangePasswordVo requestVo) {
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        if (!passwordEncoder.matches(requestVo.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_ERROR);
        }
        if (passwordEncoder.matches(requestVo.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_SAME_AS_OLD);
        }
        user.setPassword(passwordEncoder.encode(requestVo.getNewPassword()));
        this.updateById(user);
        log.info("密码修改成功：userId={}", userId);

        // 生成新 JWT Token（密码改了，旧Token虽然还能用但要返回新的）
        String newToken = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getStudentNo(), user.getRealName(), user.getRole());

        LoginResponseVo responseVo = new LoginResponseVo();
        responseVo.setUserId(user.getId());
        responseVo.setUsername(user.getUsername());
        responseVo.setStudentNo(user.getStudentNo());
        responseVo.setRealName(user.getRealName());
        responseVo.setRole(user.getRole());
        responseVo.setToken(newToken);
        return responseVo;
    }

    /**
     * 注册：校验 → 加密密码 → 保存用户 → 生成JWT → 自动登录
     * 
     * 新增学号唯一性校验：
     * - 学号和用户名一样，全局唯一
     * - 用于成绩管理、与学校系统对接
     */
    @Override
    public LoginResponseVo register(RegisterRequestVo requestVo) {
        // 1. 两次密码一致性校验
        if (!requestVo.getPassword().equals(requestVo.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_NOT_MATCH);
        }

        // 2. 用户名不能包含 admin
        if (requestVo.getUsername().toLowerCase().contains("admin")) {
            throw new BusinessException(ErrorCode.USERNAME_INVALID);
        }

        // 3. 用户名唯一性校验
        long count = this.count(
                new QueryWrapper<User>().eq("username", requestVo.getUsername()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.USERNAME_DUPLICATE);
        }

        // 4. 学号唯一性校验（新增！）
        if (requestVo.getStudentNo() != null && !requestVo.getStudentNo().isEmpty()) {
            long snoCount = this.count(
                    new QueryWrapper<User>().eq("student_no", requestVo.getStudentNo()));
            if (snoCount > 0) {
                throw new BusinessException(ErrorCode.STUDENT_NO_DUPLICATE);
            }
        }

        // 5. BCrypt 加密密码
        String encodedPassword = passwordEncoder.encode(requestVo.getPassword());

        // 6. 组装 User 对象
        User user = new User();
        user.setUsername(requestVo.getUsername());
        user.setPassword(encodedPassword);
        user.setStudentNo(requestVo.getStudentNo());  // 保存学号
        user.setRealName(requestVo.getRealName());     // 保存姓名
        user.setRole("STUDENT");
        user.setStatus("ACTIVE");

        boolean saved = this.save(user);
        if (!saved) {
            throw new BusinessException(ErrorCode.REGISTER_FAILED);
        }

        // 7. 注册成功自动登录：生成 JWT Token（包含学号）
        String token = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getStudentNo(), user.getRealName(), user.getRole());

        LoginResponseVo responseVo = new LoginResponseVo();
        responseVo.setUserId(user.getId());
        responseVo.setUsername(user.getUsername());
        responseVo.setStudentNo(user.getStudentNo());
        responseVo.setRealName(user.getRealName());
        responseVo.setRole(user.getRole());
        responseVo.setToken(token);

        log.info("用户注册成功并自动登录：username={}, studentNo={}, userId={}", 
                 user.getUsername(), user.getStudentNo(), user.getId());
        return responseVo;
    }
    /**
     * 修改个人信息（真实姓名）
     * 更新数据库后重新生成 JWT Token（包含新的 realName），前端收到后更新 localStorage 即可立即显示
     */
    @Override
    public LoginResponseVo updateProfile(Long userId, UpdateProfileVo requestVo) {
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        // 如果传了realName则更新
        if (requestVo.getRealName() != null && !requestVo.getRealName().trim().isEmpty()) {
            user.setRealName(requestVo.getRealName().trim());
            this.updateById(user);
            log.info("个人信息修改成功：userId={}, realName={}", userId, user.getRealName());
        }

        // 无论是否修改了realName，都重新生成JWT Token（确保Token中的用户信息是最新的）
        String newToken = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getStudentNo(), user.getRealName(), user.getRole());

        LoginResponseVo responseVo = new LoginResponseVo();
        responseVo.setUserId(user.getId());
        responseVo.setUsername(user.getUsername());
        responseVo.setStudentNo(user.getStudentNo());
        responseVo.setRealName(user.getRealName());
        responseVo.setRole(user.getRole());
        responseVo.setToken(newToken);
        return responseVo;
    }
}
