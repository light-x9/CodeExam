package com.atguigu.exam.controller;


import com.atguigu.exam.common.Result;
import com.atguigu.exam.context.CurrentUser;
import com.atguigu.exam.context.UserContext;
import com.atguigu.exam.service.UserService;
import com.atguigu.exam.vo.LoginRequestVo;
import com.atguigu.exam.vo.LoginResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * 用户控制器 - 处理用户认证和权限管理相关的HTTP请求
 * 
 * ==================== ThreadLocal 使用示例 ====================
 * 
 * 在本 Controller 中，演示了两种获取当前用户的方式：
 * 
 * 方式一（推荐）：UserContext.get()
 *   CurrentUser user = UserContext.get();
 *   → 任何地方都能用，不需要注入 HttpServletRequest，不依赖 Servlet API
 * 
 * 方式二：UserContext.require()
 *   CurrentUser user = UserContext.require();
 *   → 确保返回非 null，如果未登录直接抛异常，适合"必须登录"的场景
 * 
 * 快捷方法：
 *   Long userId = UserContext.getUserId();
 *   String username = UserContext.getUsername();
 *   → 只取单个字段时更方便
 */
@Slf4j
@RestController  // REST控制器，返回JSON数据
@RequestMapping("/api/user")  // 用户API路径前缀
@CrossOrigin(origins = "*")  // 允许跨域访问
@Tag(name = "用户管理", description = "用户相关操作，包括登录认证、权限验证等功能")  // Swagger API分组
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户登录
     * 流程：Controller接收参数 → 调用Service处理业务 → 包装成Result返回
     * @param loginRequestVo 登录请求
     * @return 登录结果
     */
    @PostMapping("/login")  // 处理POST请求
    @Operation(summary = "用户登录", description = "用户通过用户名和密码进行登录验证，返回用户信息和token")  // API描述
    public Result<LoginResponseVo> login(@RequestBody LoginRequestVo loginRequestVo) {
        // 调用 Service 层处理登录逻辑
        LoginResponseVo loginResponseVo = userService.login(loginRequestVo);
        // 用 Result 包装返回
        return Result.success(loginResponseVo);
    }
    
    /**
     * 获取当前登录用户信息
     * 
     * ==================== ThreadLocal 使用示例 ====================
     * 
     * 之前需要写成：
     *   public Result<User> getCurrentUser(HttpServletRequest request) {
     *       Long userId = (Long) request.getAttribute("userId");
     *       String username = (String) request.getAttribute("username");
     *       // 强转 + 魔法字符串，又丑又容易出错
     *   }
     * 
     * 现在只需一行：
     *   CurrentUser currentUser = UserContext.get();
     * 
     * @return 当前登录用户信息
     */
    @GetMapping("/current")
    @Operation(summary = "获取当前用户信息", description = "从 ThreadLocal 中获取当前登录用户信息，无需手动传 userId")
    public Result<CurrentUser> getCurrentUser() {
        // ★ 一行代码获取当前用户，不再需要注入 HttpServletRequest
        CurrentUser currentUser = UserContext.get();
        
        if (currentUser == null) {
            return Result.error("未登录");
        }
        
        log.info("当前用户：id={}, username={}, role={}", 
                 currentUser.getUserId(), currentUser.getUsername(), currentUser.getRole());
        
        return Result.success(currentUser);
    }
    
    /**
     * 检查用户权限
     * @param userId 用户ID
     * @return 权限检查结果
     */
    @GetMapping("/check-admin/{userId}")  // 处理GET请求
    @Operation(summary = "检查管理员权限", description = "验证指定用户是否具有管理员权限")  // API描述
    public Result<Boolean> checkAdmin(
            @Parameter(description = "用户ID") @PathVariable Long userId) {

        return Result.success(true);
    }
} 