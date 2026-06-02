# CodeExam — AI 智能考试系统

> 基于 Spring Boot 3 + Vue3 + AI 大模型构建的智能在线考试平台，支持 AI 智能出题、主观题异步批阅、混合题型模拟考试等功能。

---

## ✨ 项目亮点

- 🤖 **AI 出题与结构化解析**：封装 DeepSeek API 调用链路，结合 JSON Schema 校验 + 多层回退解析策略，显著提升 AI 输出结构化成功率
- ⚡ **主观题异步批阅**：配置自定义线程池，客观题本地即时判分，主观题后台异步批改，接口响应时间从 3s+ 降至 200ms 以内
- 🔐 **JWT + Redis 黑名单**：实现无状态认证与强制下线，ThreadLocal 维护用户上下文，保证请求链路隔离
- 📦 **MinIO 文件存储**：使用 presigned URL 让客户端直传对象存储，后端仅负责签名，降低服务器带宽压力

---

## 🛠 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3、MyBatis-Plus |
| 数据库 | MySQL、Redis |
| 存储 | MinIO |
| 认证 | JWT |
| AI 能力 | DeepSeek API |
| 前端 | Vue3、Element Plus、Axios |
| 其他 | 自定义线程池、Redis 黑名单 |

---

## 📌 核心功能

**用户端**：注册登录 / 在线考试 / 查看成绩与考试记录

**管理端**：题库管理 / 试卷管理 / 用户管理 / 考试管理

**AI 功能**：AI 智能出题 / 主观题自动评分 / 异步批阅队列

---

## 🚀 快速启动

**后端**
```bash
# 1. 配置 application.yml（数据库、Redis、MinIO、DeepSeek API Key）
# 2. 执行 sql/ 目录下的初始化脚本
# 3. 启动
mvn clean package
java -jar exam-system.jar
```

**前端**
```bash
npm install
npm run dev
```

---

## 📷 项目截图

（可放 1-2 张界面截图，有图胜千言）
