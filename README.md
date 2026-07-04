# CodeExam（云学宝）

基于 Spring Boot 3 + MySQL + Redis 构建的前后端分离 AI 智能学习平台

---

## 📸 项目截图

> 🖼️ 截图待补充

---

![Java 17](https://img.shields.io/badge/Java-17-orange?logo=openjdk) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.0.5-brightgreen?logo=spring) ![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?logo=mysql) ![Redis](https://img.shields.io/badge/Redis-red?logo=redis) ![Vue 3](https://img.shields.io/badge/Vue-3.3-4FC08D?logo=vue.js) ![License](https://img.shields.io/badge/License-MIT-yellow)

[在线演示]() | [API 接口文档](http://localhost:8080/doc.html) | [📖 详细架构文档](docs/ARCHITECTURE.md)

---

## ✨ 项目亮点

- **AI 出题与结果结构化容错** — 封装大模型 API 调用链路，结合 JSON Schema 校验、Markdown 提取、多层回退解析与截断恢复策略，AI 输出结构化成功率从 75% 提升至 95%+
- **AI 主观题异步批改** — 配置自定义 ThreadPoolTaskExecutor 异步执行批阅任务，客观题即时判分、主观题后台异步批改，接口响应时间从同步等待的 3s+ 降至异步解耦后的 200ms 以内
- **缓存穿透防护** — 基于 Redisson RBloomFilter 在启动时预加载全量题目 ID，非法 ID 在过滤器层直接拦截，避免无效查询穿透到数据库
- **接口幂等与并发控制** — 使用 Redisson RLock 以 userId+examId 为 key 加分布式锁，解决重复提交写入多条成绩问题，重复提交拦截成功率近乎 100%
- **JWT + Redis 黑名单双保险** — 解决 JWT 无状态认证无法主动失效的问题，支持登出、踢人等场景，配合 @RequireRole 注解 + AOP 切面实现细粒度角色权限控制

---

## 🛠️ 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| **后端框架** | Spring Boot 3 | 3.0.5 |
| | Spring MVC + Spring AOP | — |
| | MyBatis-Plus | 3.5.3.1 |
| **数据库** | MySQL | 8.0.33 |
| | Redis | — |
| | MinIO 对象存储 | — |
| **中间件** | Redisson（RLock + RBloomFilter） | 3.24.3 |
| | HikariCP 连接池 | — |
| **AI 集成** | DeepSeek API | — |
| | JSON Schema Validator（结构校验） | 1.4.0 |
| **认证授权** | JWT（JJWT 0.12.x）+ Redis 黑名单 | — |
| | @RequireRole 注解 + AOP 权限切面 | — |
| **工具** | Lombok、Knife4j、Spring Validation | — |
| **前端** | Vue 3 + Vue Router 4 | ^3.3.4 |
| | Element Plus UI | ^2.3.8 |
| | Pinia 状态管理 | ^2.1.6 |
| | Axios HTTP | ^1.4.0 |
| | Vite + ECharts | — |

---

## 🚀 快速开始

### 环境要求

- JDK 17+
- MySQL 8.0
- Redis
- Maven 3.6+
- MinIO（可选，用于视频/图片/文件存储）
- Node.js 16+（仅前端开发需要）

### 启动步骤

[//]: # (code block start)
```text
# 1. 克隆项目
git clone https://github.com/light-x9/CodeExam.git

# 2. 导入数据库
# 在 MySQL 中创建数据库 exam_system_new，执行根目录的 exam_system_new_backup.sql

# 3. 配置后端
cd CodeExam
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# 编辑 application-local.yml，配置数据库/Redis/MinIO/AI API Key

# 4. 启动后端
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 5. 启动前端
cd exam-system-web-backup
npm install
npm run dev
```
[//]: # (code block end)

后端默认运行在 http://localhost:8080，前端默认运行在 http://localhost:5173。

### 默认账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | admin123 |
| 教师 | teacher | teacher123 |
| 学生 | student | student123 |

---

## 📁 项目结构

### 🏠 后端（exam_system_server）

```
src/main/java/com/atguigu/exam/
├── annotation/       # @RequireRole、@OperationLog 自定义注解
├── aspect/           # AOP 切面（权限校验、操作日志）
├── common/           # Result 统一返回、全局异常处理、错误码
├── config/           # Redis、Redisson、MinIO、JWT 等 11 个配置类
├── context/          # ThreadLocal 用户上下文
├── controller/       # 14 个 REST API 控制器
├── entity/           # 19 个数据库表实体
├── mapper/           # 16 个 Mapper + 12 个 XML 映射
├── service/          # 19 个 Service 接口 + 实现类
├── utils/            # JwtUtil、RedisUtils、ExcelUtil 等
└── vo/               # 23 个数据传输对象（VO）
```

### 📱 前端（exam-system-web-backup）

基于 Vue 3 + Element Plus 构建的管理后台，覆盖考试、题库、视频学习三大核心模块：

- **考试模块** — 在线考试、成绩查看、排行榜、错题回顾
- **题库管理** — 题目 CRUD、AI 智能出题、Excel 批量导入、分类管理
- **视频学习** — 视频上传与管理、分类浏览、学习记录
- **系统管理** — 用户管理、公告管理、轮播图管理、数据统计（ECharts 图表）
- **状态管理** — Pinia 管理全局状态，Axios 封装 HTTP 请求，Vue Router 处理路由导航

---

## 📖 详细设计文档

完整的**分层架构说明、核心业务流程图、数据库表设计、代码级实现解析**请查看：
👉 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

---

## 📝 License

本项目仅用于学习交流，请勿用于商业用途。