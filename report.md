# 项目全面分析报告

## 一、项目概览

| 维度 | 详情 |
|------|------|
| **技术栈** | Spring Boot 3.0.5 + MyBatis-Plus 3.5.3.1 + MySQL 8.x + Redis + JWT(jjwt 0.12.5) + BCrypt + Redisson 3.24.3 + Knife4j 4.3.0 + MinIO + WebClient(Kimi API) + Apache POI |
| **Java 版本** | Java 17 |
| **项目类型** | 在线考试系统后端（尚硅谷/黑马培训课程项目） |
| **代码行数** | 约 35 个 Java 文件，估计 ~5000 行左右 |
| **测试** | 0 个测试文件 |

### 已实现的功能模块

| 模块 | 完成度 | 说明 |
|------|--------|------|
| 用户认证（登录/注册/改密/退出） | 90% | JWT + BCrypt + Redis 黑名单，较完整 |
| 题目管理（CRUD + 分页 + Excel + AI生成） | 85% | 核心逻辑完整，AI 生成有重试机制 |
| 分类管理（树形结构） | 85% | 树形构建算法不错，含题目数量统计 |
| 试卷管理（手动组卷） | 60% | 手动组卷可用，但 AI 组卷、更新、发布等都是空壳 |
| 轮播图管理 | 50% | 查询和状态切换可用，创建/更新是 TODO |
| 公告管理 | 80% | 基本可用 |
| 统计模块 | 70% | 可用但很简陋 |
| 视频管理 | 70% | 点赞/观看/投稿/审核已实现，但文件上传是空壳 |
| 文件服务 | 40% | 只有本地文件读取，MinIO 上传未实现 |
| **考试流程（核心！）** | **5%** | **开始考试/提交答案/AI批阅/成绩查询全是空壳** |
| **Kimi AI 批阅** | **0%** | `KimiGradingServiceImpl` 完全为空 |

---

## 二、缺少的功能

### 2.1 考试核心流程完全缺失（最关键的问题）
- `ExamController` 全部 5 个方法都返回 `null` 或固定字符串，没有任何实际业务逻辑
- `ExamServiceImpl`、`ExamRecordServiceImpl` 类体完全为空
- `KimiGradingServiceImpl` 类体完全为空 — AI 批阅功能不存在
- 考试排行榜接口返回 `null`

### 2.2 文件上传模块是空壳
- `FileUploadServiceImpl` 类体为空
- `VideoServiceImpl` 中的上传代码被注释，写了 `//todo: 文件上传以后开放即可！`

### 2.3 试卷管理半成品
- `updatePaper()` 直接 `return Result.success(null)`，无实际逻辑
- `createPaperWithAI()` 直接 `return Result.success(null)`，无实际逻辑
- `getPaperById()`、`updatePaperStatus()`、`deletePaper()` 全是空壳

### 2.4 缺少的关键非功能模块
- **没有测试** — 0 个测试文件，这是最大的工程化短板
- **没有接口限流** — 虽然引入了 Redisson，但没用到
- **没有操作日志持久化** — `OperationLogAspect` 只打印到控制台，没有写入数据库
- **没有数据校验层** — 很多接口参数没有 `@Valid` 校验
- **没有多环境配置** — 只有一个 `application.yml`
- **没有 API 版本管理**
- **没有统一错误码枚举** — 全部用硬编码数字

---

## 三、工程化不足的地方

### 3.1 异常处理混乱
```java
// NoticeServiceImpl.java — Service 层直接返回 Result，违反分层原则
public Result<List<Notice>> getActiveNotices() {
    return Result.success(notices);  // Service 不应该知道 HTTP 层的 Result
}
```
其他 Service（如 `UserServiceImpl`）又是抛异常让 Controller 统一处理，两种风格混用。

### 3.2 大量 System.out.println 调试代码
`LoginInterceptor`、`QuestionController`、`PermissionAspect`、`OperationLogAspect`、`QuestionServiceImpl` 中充斥着大量学习用的 `System.out.println` 方块日志。生产代码不应保留这些。

### 3.3 配置硬编码与敏感信息泄露
```yaml
# application.yml 中硬编码了生产密钥！
kimi.api.api-key: sk-p6OuHxjnFy3uXCAWAGs9TUeuer8OfuknMl39ePyQ9G3o3SHo  # 真实 API Key
spring.datasource.password: root
spring.data.redis.password: 209746
jwt.secret: exam_system_jwt_secret_key_2024_level3_learning
```

### 3.4 线程使用不当
```java
// QuestionServiceImpl.java:80 — 每次查询都 new Thread，没有用线程池
new Thread(() -> {
    incrementQuestionScore(question.getId());
}).start();
```
在高并发下这会创建大量线程，应该用 `@Async` + 线程池。

### 3.5 缺少缓存策略
- 虽然定义了 `CacheConstants`、`RedisUtils`，但题目详情查询的缓存是注释状态（`//5. 预留：进行redis的数据缓存zset`）
- 热门题目缓存也是空壳
- 没有 Cache-Aside、读写穿透等缓存模式

### 3.6 数据库相关
- 没有数据库初始化脚本（SQL 文件）
- 没有数据库版本管理（Flyway/Liquibase）
- `create_time` / `update_time` 使用 `Date` 类型而非 `LocalDateTime`
- MyBatis XML Mapper 中可能没有充分利用

---

## 四、安全问题

### 4.1 严重：敏感信息明文存储
`application.yml` 中明文硬编码了 Kimi API Key、数据库密码、Redis 密码。**如果代码推送到 GitHub，这些密钥已经泄露。**

### 4.2 高危：CORS 全放行
```java
@CrossOrigin(origins = "*")  // 几乎所有 Controller 都有这行
```
允许任意来源跨域请求，配合 JWT 认证，攻击者可以从任意网站发起 CSRF 攻击。

### 4.3 中危：JWT Secret 强度不足
```yaml
jwt.secret: exam_system_jwt_secret_key_2024_level3_learning
```
这是教学用的弱密钥，不足 256 bits，可被暴力破解。

### 4.4 中危：用户枚举风险
```java
// UserServiceImpl.java login() 方法
if (user == null) {
    throw new RuntimeException("用户名或密码错误");  // 统一消息，还好
}
```
登录这里的处理较好（用户名和密码错误返回同一消息）。但注册接口的"用户名已被注册"暴露了用户名存在性。

### 4.5 低危：路径穿越已有防护
`FileController.getFile()` 中做了 `canonicalPath` 检查，这点值得肯定。

### 4.6 低危：缺少输入过滤
没有 XSS 过滤器，题目内容、公告内容等富文本字段没有做 HTML 转义。

---

## 五、高并发风险

### 5.1 无并发控制的写入操作
```java
// QuestionServiceImpl.saveQuestion() — 先查后写，无锁保护
long count = count(queryWrapper);  // 查询
if (count > 0) throw ...;
save(question);  // 写入
```
高并发下，两个请求同时查到 `count=0`，都会执行 `save()`，导致重复数据。应该在数据库层面建唯一索引 + 捕获 `DuplicateKeyException`。

### 5.2 视频点赞的并发问题
```java
// VideoServiceImpl.toggleVideoLike() — 查-判断-写 无锁
boolean isLiked = videoLikeMapper.isLikedByIp(videoId, userIp);
if (isLiked) { videoMapper.decrementLikeCount(videoId); }
else { videoMapper.incrementLikeCount(videoId); }
```
`likeCount` 的增减在 Java 层做而非数据库原子操作（`UPDATE ... SET like_count = like_count + 1`），存在丢失更新风险。

### 5.3 视频观看计数
`videoMapper.incrementViewCount(videoId)` 是每次观看直接 UPDATE 数据库，高并发下会锁行。应该用 Redis 计数器 + 定时刷回 MySQL。

### 5.4 Redisson 引入但未使用
pom.xml 中引入了 Redisson 分布式锁，但代码中一次都没用到。

---

## 六、"面试项目" vs "真实项目" 的特征

### 明显的"面试/培训项目"特征：
1. **注释量极大，且是教学风格** — 每个类、每个方法都有长篇教学注释，解释"什么是 JWT"、"什么是 AOP"、"什么是 ThreadLocal"，这在真实项目中不会出现
2. **大量 `System.out.println` 方块日志** — 用于演示请求链路，真实项目会用 MDC + traceId
3. **大量 TODO 标记** — 核心考试功能全是空壳
4. **package 名为 `com.atguigu.exam`** — 典型的培训机构包名
5. **`@author 智能学习平台开发团队`** — 培训项目的署名风格
6. **核心功能缺失** — 考试系统最核心的"考试"功能不存在
7. **零测试** — 真实项目至少会有 Service 层的单元测试
8. **所有异常用 `RuntimeException`** — 没有定义业务异常体系

### 值得肯定的"亮点"：
1. ThreadLocal 用户上下文的设计思路正确，`afterCompletion` 中清理也做了
2. JWT + Redis 黑名单的双重校验方案完整
3. AOP 权限校验 + 操作日志的设计模式正确
4. BCrypt 密码加密
5. 分类树形结构的构建算法不错（Stream API + groupingBy）
6. 文件路径穿越防护
7. AI 生成题目的重试机制

---

## 七、作为 Java 后端实习项目的优化建议（按优先级排序）

### Tier 1 — 必须补全（核心功能）
1. **实现考试流程** — 开始考试 → 提交答案 → 批阅 → 查看成绩，这是考试系统的基本闭环
2. **实现文件上传** — MinIO 集成
3. **实现 AI 批阅** — 对接 Kimi API 做简答题智能批改
4. **补全试卷管理** — 试卷发布/停用/更新/删除/详情

### Tier 2 — 工程化提升（面试亮点）
5. **引入全局异常码枚举** — 替代硬编码的 400/409/500
6. **引入 MapStruct** — 替代 `BeanUtils.copyProperties`，编译期生成映射代码，性能更好
7. **使用线程池** — 替换 `new Thread()` 为 `@Async` + `ThreadPoolTaskExecutor`
8. **缓存实战** — 题目详情用 Redis 做 Cache-Aside 模式，热门题目用 Sorted Set
9. **添加单元测试** — JUnit 5 + Mockito，至少覆盖 Service 层

### Tier 3 — 高级特性（差异化竞争）
10. **Redisson 分布式锁实战** — 在组卷、扣减库存等场景使用
11. **接口限流** — 用 Redis + Lua 脚本或 Redisson RRateLimiter 实现
12. **敏感配置加密** — 用 Jasypt 或 Spring Cloud Config 加密 `application.yml`
13. **请求追踪** — MDC + traceId，贯穿整个请求链路
14. **操作日志入库** — 把 `OperationLogAspect` 的日志写入数据库而非仅控制台
15. **数据库唯一索引 + 乐观锁** — 解决并发写入问题
16. **多环境配置** — `application-dev.yml` / `application-prod.yml`
17. **Docker 部署** — 编写 Dockerfile + docker-compose.yml

### Tier 4 — 简历杀手锏
18. **秒杀场景模拟** — 考试名额限制 + Redis 库存扣减 + Redisson 分布式锁 + 消息队列削峰
19. **接口幂等性** — Token 机制防止重复提交考试
20. **WebSocket 实时通信** — 考试倒计时、防作弊监控
21. **数据库慢查询优化** — 分析 SQL、添加索引、Explain 分析

---

### 总结

这个项目的**基础骨架搭得不错**（JWT、ThreadLocal、AOP、MyBatis-Plus 集成），但它本质上是一个**教学演示项目**，离"能上生产"还有很大距离。最大的问题是**考试核心流程完全缺失**——一个考试系统连"考试"功能都没实现。

如果作为实习项目，建议优先把考试流程闭环做出来，然后把文件上传接上，再补 2-3 个高级特性（如 Redis 缓存 + 分布式锁 + 限流），最后的简历效果会好很多。
