package com.atguigu.exam.context;

import lombok.extern.slf4j.Slf4j;

/**
 * 用户上下文工具类 —— 基于 ThreadLocal 的请求级用户信息存储
 * 
 * ==================== 核心概念：什么是 ThreadLocal？ ====================
 * 
 * ThreadLocal 是 Java 提供的一个线程局部变量工具。通俗理解：
 * 
 *   "每个线程都有一个私有的小盒子（Map），ThreadLocal 就是这个盒子的钥匙。
 *    你用同一把钥匙在不同线程里存东西，每个线程拿到的都是自己的那份，
 *    互相不干扰。"
 * 
 *   线程A：UserContext.set(userA) → 线程A的盒子里存了 userA
 *   线程B：UserContext.set(userB) → 线程B的盒子里存了 userB
 *   线程A：UserContext.get()      → 拿到 userA（不会是 userB！）
 * 
 * ==================== 为什么 ThreadLocal 适合当前请求存储用户信息？ ====================
 * 
 * 1. Web 容器（Tomcat）采用线程池模型：一个请求绑定一个线程
 *      用户请求 → Tomcat 从线程池分配线程 → 请求处理完 → 线程归还线程池
 *      请求的整个生命周期都在同一个线程内完成！
 * 
 * 2. 请求处理链路是"单线程串行"的：
 *      LoginInterceptor → AOP PermissionAspect → Controller → Service → AOP OperationLogAspect
 *      所有这些环节都在同一条线程里执行，ThreadLocal.set() 一次，处处可读。
 * 
 * 3. 天然线程隔离，不需要锁：
 *      并发场景下，100个请求 = 100个线程，各自有自己的 ThreadLocal 副本，
 *      不会出现"线程A读到线程B的用户"这种问题，也不需要加 synchronized。
 * 
 * 4. 解耦 Servlet API：
 *      之前必须注入 HttpServletRequest 才能拿用户信息，
 *      现在任何地方（包括纯业务 Service、工具类）都能用 UserContext.get()，
 *      不再依赖 Web 层。
 * 
 * ==================== 为什么 finally 中必须 remove()？ ====================
 * 
 * 这是 ThreadLocal 最容易踩的坑！原因有三：
 * 
 * 【原因1：内存泄漏】
 *   ThreadLocal 内部结构简化示意：
 *     Thread → ThreadLocalMap → Entry(弱引用ThreadLocal, 强引用value)
 *   
 *   当 ThreadLocal 实例（我们的 USER_CONTEXT）被 GC 回收后，
 *   Entry 里的 key 变成 null，但 value（CurrentUser 对象）因为是强引用，
 *   永远不会被回收！这就是"内存泄漏"。
 *   
 *   调用 remove() 会直接把整个 Entry 删掉，彻底断开引用链。
 * 
 * 【原因2：Tomcat 线程复用导致"脏数据"】
 *   Tomcat 用线程池处理请求（默认最多200个线程）：
 *   
 *     请求1（用户admin）→ 线程A → UserContext.set(admin) → 请求处理完
 *                                     ↑ 没有 remove()！
 *     请求2（用户teacher）→ 复用线程A → UserContext.get() → 拿到的是 admin！！
 *                                     ↑ 严重的串号问题！
 *   
 *   用户 teacher 的操作被记录成 admin 做的，日志混乱、权限混乱。
 *   这就是真实生产环境最常见的 ThreadLocal 事故！
 * 
 * 【原因3：线程池中无限增长】
 *   线程池的线程不会销毁，如果每次请求都 set 但不 remove，
 *   所有线程的 ThreadLocalMap 里的 Entry 永远存在（即使 key 被 GC），
 *   虽然 value 不会无限增长（每个线程只有一个），但弱引用 Entry 本身会占用内存。
 * 
 * 正确做法：
 *   try {
 *       UserContext.set(currentUser);
 *       // ... 处理请求 ...
 *   } finally {
 *       UserContext.remove();  // ← 不管成功还是异常，必须清理！
 *   }
 * 
 * ==================== ThreadLocal 与 Tomcat 线程复用的关系 ====================
 * 
 * Tomcat 线程池生命周期：
 * 
 *   启动时：创建 corePoolSize 个线程（默认10）
 *   请求来：从池中取一个空闲线程 → 处理请求 → 线程归还池（不销毁）
 *   请求多：临时创建新线程（最多 maxPoolSize=200）
 *   空闲久：多余线程超时销毁
 * 
 * 由于线程复用的存在，如果请求A在归还线程前没清理 ThreadLocal，
 * 请求B拿到同一个线程后就会读到请求A的残留数据。
 * 
 * 这不是 Tomcat 的问题，是所有线程池模型的通用问题。
 * 
 * ==================== 为什么这种设计比手动传 userId 更优雅？ ====================
 * 
 * 【之前：手动传参】
 *   Controller:
 *     public Result deleteQuestion(Long questionId, HttpServletRequest request) {
 *         Long userId = (Long) request.getAttribute("userId");
 *         questionService.delete(questionId, userId);  // 每个方法都要传 userId
 *     }
 *   
 *   Service:
 *     public void delete(Long questionId, Long userId) {
 *         log.info("用户{}删除了题目{}", userId, questionId);
 *         // 每个 Service 方法签名都要带 userId 参数
 *     }
 * 
 * 问题：
 *   - userId 像一个"幽灵参数"，从 Controller 逐层传到 Service、Mapper
 *   - 污染了所有方法签名，业务方法和横切关注点耦合在一起
 *   - 如果哪天要加 tenantId（多租户），要改几十个方法签名
 * 
 * 【现在：UserContext】
 *   Controller:
 *     public Result deleteQuestion(Long questionId) {
 *         questionService.delete(questionId);
 *         // 不需要传 userId！
 *     }
 *   
 *   Service:
 *     public void delete(Long questionId) {
 *         CurrentUser user = UserContext.get();
 *         log.info("用户{}删除了题目{}", user.getUserId(), questionId);
 *         // 方法签名干净了，只关心业务参数
 *     }
 * 
 * 本质区别：
 *   - 手动传参 = 纵向传递（方法参数链），每层都要参与
 *   - UserContext = 横向共享（线程本地存储），需要的地方自己取
 *   - 这符合 AOP 的思想：横切关注点（用户身份）不应该侵入业务代码
 * 
 * ==================== 在整个请求链路中的作用 ====================
 * 
 *  ① LoginInterceptor.preHandle()
 *       解析 JWT → CurrentUser(userId, username, role) → UserContext.set()
 *       ↓
 *  ② PermissionAspect.@Around()
 *       CurrentUser user = UserContext.get()
 *       检查 user.getRole() 是否满足 @RequireRole 要求
 *       ↓
 *  ③ Controller
 *       CurrentUser user = UserContext.get()  // 不需要注入 HttpServletRequest！
 *       用 user.getUserId() 做业务
 *       ↓
 *  ④ Service
 *       CurrentUser user = UserContext.get()  // 不需要从 Controller 传 userId！
 *       用 user.getUserId() 做数据过滤
 *       ↓
 *  ⑤ OperationLogAspect.@AfterReturning()
 *       CurrentUser user = UserContext.get()
 *       记录：user.getUsername() 执行了什么操作
 *       ↓
 *  ⑥ LoginInterceptor.afterCompletion()
 *       UserContext.remove()  // ★ 关键：必须清理！
 * 
 * @author light
 */
@Slf4j
public final class UserContext {

    /**
     * ThreadLocal 实例 —— 每个线程独立的"用户信息存储盒"
     * 
     * 为什么用 private static final？
     * - private：外部不能直接操作 ThreadLocal，必须通过 set/get/remove 方法
     * - static：整个 JVM 只有这一个 ThreadLocal 实例（所有线程共用这把"钥匙"）
     * - final：防止被意外重新赋值
     */
    private static final ThreadLocal<CurrentUser> USER_CONTEXT = new ThreadLocal<>();

    /**
     * 私有构造器 —— 工具类不应该被实例化
     */
    private UserContext() {
        throw new UnsupportedOperationException("工具类不能被实例化");
    }

    /**
     * 将当前登录用户存入当前线程的 ThreadLocal
     * 
     * 调用时机：LoginInterceptor 中，JWT 校验成功后
     * 
     * @param currentUser 当前登录用户信息（不可为 null）
     */
    public static void set(CurrentUser currentUser) {
        if (currentUser == null) {
            throw new IllegalArgumentException("currentUser 不能为 null");
        }
        USER_CONTEXT.set(currentUser);
        log.debug("UserContext 已设置：userId={}, username={}, role={}", 
                  currentUser.getUserId(), currentUser.getUsername(), currentUser.getRole());
    }

    /**
     * 从当前线程的 ThreadLocal 中获取用户信息
     * 
     * 调用时机：
     * - Controller / Service 中需要当前用户信息时
     * - AOP 切面（权限校验、操作日志）中
     * - 任何需要在请求链路中知道"谁在操作"的地方
     * 
     * @return 当前登录用户，如果未登录返回 null
     */
    public static CurrentUser get() {
        return USER_CONTEXT.get();
    }

    /**
     * 获取当前登录用户（如果不存在则抛出异常）
     * 
     * 适合在"确定已登录"的上下文使用，省去 null 检查。
     * 比如在 Service 层，业务逻辑执行前必定已通过拦截器+权限校验。
     * 
     * @return 当前登录用户（保证非 null）
     * @throws IllegalStateException 如果当前线程未绑定用户信息
     */
    public static CurrentUser require() {
        CurrentUser user = USER_CONTEXT.get();
        if (user == null) {
            throw new IllegalStateException("当前线程未绑定用户信息，请确保请求已通过登录拦截器");
        }
        return user;
    }

    /**
     * 获取当前用户ID（快捷方法）
     * 
     * @return 用户ID，未登录返回 null
     */
    public static Long getUserId() {
        CurrentUser user = get();
        return user != null ? user.getUserId() : null;
    }

    /**
     * 获取当前用户名（快捷方法）
     * 
     * @return 用户名，未登录返回 null
     */
    public static String getUsername() {
        CurrentUser user = get();
        return user != null ? user.getUsername() : null;
    }

    /**
     * 获取当前用户角色（快捷方法）
     * 
     * @return 角色，未登录返回 null
     */
    public static String getRole() {
        CurrentUser user = get();
        return user != null ? user.getRole() : null;
    }

    /**
     * ★ 核心方法：清除当前线程的 ThreadLocal 数据
     * 
     * 调用时机：LoginInterceptor.afterCompletion() 的 finally 块中
     * 
     * 为什么必须调用？
     * 1. 防止内存泄漏（ThreadLocalMap 的 Entry 是对 value 的强引用）
     * 2. 防止 Tomcat 线程复用时读到上一个请求的脏数据
     * 3. 防止线程池场景下 ThreadLocal 数据无限积累
     * 
     * 详细分析见类级注释。
     */
    public static void remove() {
        CurrentUser user = USER_CONTEXT.get();
        USER_CONTEXT.remove();
        if (user != null) {
            log.debug("UserContext 已清理：userId={}, username={}", 
                      user.getUserId(), user.getUsername());
        }
    }
}
