# AI智能考试系统

## 项目简介

基于 Spring Boot + Vue3 + AI 大模型构建的智能在线考试平台。

支持题库管理、试卷管理、在线考试、自动判卷、AI主观题批阅等功能。

## 技术栈

后端：

* Java 17
* Spring Boot
* MyBatis Plus
* MySQL
* Redis
* JWT
* MinIO

前端：

* Vue3
* Element Plus
* Axios

AI能力：

* DeepSeek API
* AI智能出题
* AI主观题批阅

## 核心功能

### 用户端

* 用户注册登录
* 在线考试
* 查看成绩
* 查看考试记录

### 管理端

* 题库管理
* 试卷管理
* 用户管理
* 考试管理

### AI功能

* AI生成题目
* AI主观题评分
* 异步判卷

## 项目亮点

* JWT无状态认证
* Redis黑名单机制
* ThreadLocal用户上下文
* MinIO文件上传
* AI自动判卷
* 自定义线程池异步批阅

## 项目启动

### 后端

```bash
mvn clean package
java -jar exam-system.jar
```

### 前端

```bash
npm install
npm run dev
```
