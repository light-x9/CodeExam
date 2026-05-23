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
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // ========== 第2步：校验角色 —— 只有管理员才能登录后台 ==========
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            log.warn("登录失败：非管理员用户尝试登录后台 - {} (role={})",
                     loginRequestVo.getUsername(), user.getRole());
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "仅管理员可登录后台");
        }

        // ========== 第3步：BCrypt 加密比对密码 ==========
        // Level 1 用的是：password.equals(user.getPassword()) —— 明文比对，不安全
        // Level 2 升级为：passwordEncoder.matches(明文, 密文) —— 加密比对
        // matches() 内部：把输入的明文用同样的盐值加密一次 → 比对结果
        if (!passwordEncoder.matches(loginRequestVo.getPassword(), user.getPassword())) {
            log.warn("登录失败：密码错误 - {}", loginRequestVo.getUsername());
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
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
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // ========== 第2步：校验旧密码 ==========
        // BCrypt.matches(明文, 密文) 内部流程：
        //   1. 从密文中提取盐值（$2a$10$盐值$密文）
        //   2. 用同样的盐值加密输入的明文
        //   3. 比对两个密文是否相同
        if (!passwordEncoder.matches(requestVo.getOldPassword(), user.getPassword())) {
            log.warn("修改密码失败：旧密码错误 - userId={}", userId);
            throw new BusinessException(ErrorCode.OLD_PASSWORD_ERROR);
        }

        // ========== 第3步：校验新旧密码不能相同 ==========
        if (requestVo.getOldPassword().equals(requestVo.getNewPassword())) {
            log.warn("修改密码失败：新旧密码相同 - userId={}", userId);
            throw new BusinessException(ErrorCode.PASSWORD_SAME_AS_OLD);
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

    /**
     * 用户注册
     * 
     * ==================== 完整注册流程 ====================
     * 
     * ┌─ 第1步：校验两次密码是否一致 ──────────────────────┐
     * │  password vs confirmPassword                        │
     * │  → 不一致：throw BusinessException(400, "...")       │
     * └────────────────────────────────────────────────────┘
     *   ↓
     * ┌─ 第2步：校验敏感词 ────────────────────────────────┐
     * │  用户名不能包含 "admin"（不区分大小写）              │
     * │  → 包含：throw BusinessException(400, "用户名不合法") │
     * └────────────────────────────────────────────────────┘
     *   ↓
     * ┌─ 第3步：校验用户名是否重复 ────────────────────────┐
     * │  用 QueryWrapper 查 users 表，count() 只查数量       │
     * │  → 已存在：throw BusinessException(409, "...")      │
     * │    409 Conflict —— HTTP 冲突状态码，表示资源已存在   │
     * └────────────────────────────────────────────────────┘
     *   ↓
     * ┌─ 第4步：BCrypt 加密密码 ───────────────────────────┐
     * │  passwordEncoder.encode(明文) → $2a$10$...密文...   │
     * │  数据库永远不会存明文密码                            │
     * └────────────────────────────────────────────────────┘
     *   ↓
     * ┌─ 第5步：设置默认属性 ──────────────────────────────┐
     * │  role   = "STUDENT"  （最小权限原则）               │
     * │  status = "ACTIVE"   （新用户默认启用）              │
     * │  注意：createTime / updateTime 由 BaseEntity 管理    │
     * └────────────────────────────────────────────────────┘
     *   ↓
     * ┌─ 第6步：保存到数据库 ──────────────────────────────┐
     * │  this.save(user) → MyBatis Plus 自动填充 createTime  │
     * │  如果 username 撞了唯一索引，数据库抛异常             │
     * │  → GlobalExceptionHandler 统一捕获并返回 500 错误    │
     * └────────────────────────────────────────────────────┘
     *   ↓
     * ┌─ 第7步：生成 JWT Token，注册成功即自动登录 ────────┐
     * │  jwtUtil.generateToken(id, username, role)           │
     * │  组装 LoginResponseVo（userId + username + token）   │
     * │  前端收到后存 localStorage，直接跳转首页             │
     * │  无需用户再手动登录一次                              │
     * └────────────────────────────────────────────────────┘
     * 
     * ==================== 为什么用 BusinessException 而不是 RuntimeException？ ====================
     * 
     * BusinessException 可以携带自定义状态码：
     * - 400：参数校验失败（如密码不一致、用户名含敏感词……）
     * - 409：用户名冲突（前端可据此提示"该用户名已被注册"）
     * 
     * 如果用 RuntimeException，只能统一返回 500，前端无法区分错误类型。
     * 
     * ==================== 密码安全性 ====================
     * 
     * passwordEncoder.encode() 内部流程：
     *   1. 随机生成一个盐值（Salt）
     *   2. 用盐值 + BCrypt 算法加密明文
     *   3. 返回格式化字符串：$2a$10$盐值$密文
     * 每次调用 encode 生成的密文都不一样（盐值随机）
     * 但 matches(明文, 密文) 总能正确比对（因为盐值存在密文里）
     * 
     * @param requestVo 注册请求参数（用户名、密码、确认密码）
     * @return 登录响应（含 JWT Token），注册成功即自动登录
     */
    @Override
    public LoginResponseVo register(RegisterRequestVo requestVo) {

        // ========== 第1步：校验两次密码是否一致 ==========
        // 为什么不在 VO 层用注解校验？
        // → Jakarta Validation 的注解是单字段校验（@NotBlank、@Size 等）
        // → 跨字段校验（password vs confirmPassword）需要自定义注解，
        //    但场景简单时写在 Service 层更直观，不需要额外造轮子
        if (!requestVo.getPassword().equals(requestVo.getConfirmPassword())) {
            log.warn("注册失败：两次密码不一致 - username={}", requestVo.getUsername());
            // 400 Bad Request —— 前端提交的数据有误
            throw new BusinessException(ErrorCode.PASSWORD_NOT_MATCH);
        }

        // ========== 第2步：校验用户名是否包含敏感词admin（不区分大小写） ==========
        // toLowerCase() 先把用户名转成全小写，再 contains("admin") 判断是否包含
        // 这样不管用户输入的是 Admin、ADMIN、aDmIn……只要含有这5个字母就算违规
        if (requestVo.getUsername().toLowerCase().contains("admin")) {
            log.warn("注册失败：用户名包含敏感词 - username={}", requestVo.getUsername());
            throw new BusinessException(ErrorCode.USERNAME_INVALID);
        }

        // ========== 第3步：校验用户名是否重复 ==========
        // 构建查询条件：username = ?
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", requestVo.getUsername());
        // count() 只查数量，比 getOne() 性能更好（SELECT COUNT(1) vs SELECT *）
        long count = this.count(queryWrapper);

        if (count > 0) {
            log.warn("注册失败：用户名已存在 - username={}", requestVo.getUsername());
            // 409 Conflict —— 资源冲突，用户名已被占用
            throw new BusinessException(ErrorCode.USERNAME_DUPLICATE);
        }

        // ========== 第4步：BCrypt 加密密码 ==========
        String encodedPassword = passwordEncoder.encode(requestVo.getPassword());

        // ========== 第5步：组装 User 对象，设置默认属性 ==========
        User user = new User();
        user.setUsername(requestVo.getUsername());
        user.setPassword(encodedPassword);          // ★ BCrypt 密文，不是明文！
        user.setRole("STUDENT");                    // ★ 默认学生角色，遵循最小权限原则
        user.setStatus("ACTIVE");                   // ★ 新用户默认启用
        // realName 暂时为空，用户可在个人中心补充
        // createTime / updateTime 由 BaseEntity 自动管理（MyBatis Plus 字段填充）

        // ========== 第6步：保存到数据库 ==========
        // this.save() 是 IService 提供的方法，等价于 baseMapper.insert(user)
        // MyBatis Plus 会自动设置 createTime 和 updateTime（如果配置了自动填充）
        boolean saved = this.save(user);

        if (!saved) {
            // 理论上不会走到这里，但防御性编程总不会错
            log.error("注册失败：数据库保存失败 - username={}", requestVo.getUsername());
            throw new BusinessException(ErrorCode.REGISTER_FAILED);
        }

        // ========== 第7步：生成 JWT Token，注册成功即自动登录 ==========
        // JWT 里放了 userId、username、role 三样信息
        // 后续请求只要带上这个 Token，服务器就能知道是谁、什么角色
        // 前端收到 LoginResponseVo 后存入 localStorage，下一次请求时
        // request.js 拦截器会自动从 localStorage 取出 Token 放进 Authorization 请求头
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        LoginResponseVo responseVo = new LoginResponseVo();
        responseVo.setUserId(user.getId());
        responseVo.setUsername(user.getUsername());
        responseVo.setRealName(user.getRealName());
        responseVo.setRole(user.getRole());
        responseVo.setToken(token);

        log.info("用户注册成功并自动登录：username={}, userId={}, role=STUDENT", 
                 user.getUsername(), user.getId());
        return responseVo;
    }

} 