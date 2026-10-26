# JDEC Platform 🚀

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-blue)](https://www.oracle.com/java/technologies/downloads/#java21)
[![Sa-Token](https://img.shields.io/badge/Sa--Token-1.45.0-orange)](https://sa-token.cc/)
[![Spring Modulith](https://img.shields.io/badge/Spring%20Modulith-1.4.10-blueviolet)](https://spring.io/projects/spring-modulith)

**JDEC Platform** 是一个演进中的 **企业级模块化单体架构 (Modular Monolith)** 平台。基于 Java 21 和 Spring Boot 3 构建，利用 Spring Modulith 实现强健的模块隔离，支持多业务域的高内聚开发与动态数据扩展。

---

## 🏗️ 核心架构与模块说明

项目采用 Maven 多模块结构，严格遵循 **API-BIZ 分离** 的契约化开发模式。

### 1. 模块矩阵
| 模块名称 | 职责描述 | 核心技术 |
| :--- | :--- | :--- |
| `jdec-platform-app` | **启动组装中心** | Spring Boot, YAML Config |
| `jdec-platform-shared` | **公共基础设施** | JWT, Sa-Token, Dynamic DS, MyBatis-Plus |
| `jdec-platform-auth` | **认证与 SSO 域** | Sa-Token SSO, Enterprise WeChat Login |
| `jdec-platform-config` | **平台配置与动态引擎** | jOOQ, Dynamic Query, Metadata |
| `jdec-platform-hr` | **人力资源管理域** | MyBatis-Plus, Multi-Tenant |

### 2. 多数据源布局
平台支持动态路由多数据源，已配置的数据源包括：
*   `primary`: 系统基础库。
*   `auth_center`: 统一认证中心数据库。
*   `config_center`: 配置中心元数据库。
*   `hr_manage`: 人力资源业务数据库。
*   `school` & `third`: 外部业务/三方对接库。

---

## 🚀 核心特性

### 1. 动态数据查询
平台内置了高度灵活的动态查询机制，支持通过配置元数据实现业务数据的快速检索：
*   **端点**: `POST /api/data/{moduleCode}`
*   **功能**: 支持多字段排序 (`sorts`)、多种操作符过滤 (`filters`)、分页以及复杂逻辑字段查询。
*   **安全**: 内置字段级权限验证，确保数据访问的安全合规。

### 2. 增强安全体系 (Sa-Token + SSO)
*   **Sa-Token 整合**: 深度集成 Sa-Token 1.45.0，支持分布式会话与 Redis 存储。
*   **SSO 单点登录**: 支持高级单点登录与注销逻辑，适配多种业务系统的集成需求。
*   **JWT 令牌**: 提供可配置的 JWT 签发与校验机制。

### 3. 企业微信多主体集成
*   支持同时管理多个企业微信主体（如：环境、电力、能源等公司主体）。
*   统一的 AccessToken 缓存管理与消息推送服务。

---

## 💻 开发与运行指南

### 环境要求
*   **JDK 21** (利用虚拟线程与模式匹配特性)
*   **MySQL 8.0+**
*   **Redis** (用于 Sa-Token 会话存储)

### 构建指令
项目使用 Spotless 进行强制代码格式化（Google Java Format AOSP 风格）：
```bash
# 一键格式化并编译
./mvnw spotless:apply clean install -DskipTests
```

### 启动应用
```bash
# 默认使用 dev 配置启动
./mvnw spring-boot:run -pl jdec-platform-app
```

---

## 🌐 核心 API 概览

### 认证管理 (`/api/config/auth`)
*   `POST /login`: 用户登录。
*   `POST /logout`: 退出登录。
*   `GET /userInfo`: 获取当前人员详情。

### 数据服务 (`/api/data`)
*   `POST /{moduleCode}`: 执行动态数据检索。
    *   Header 需携带 `X-Tenant-Id` 用于自动切换目标数据源。

### 接口文档
🔗 [http://localhost:8080/doc.html](http://localhost:8080/doc.html) (Knife4j)

---

## 🚨 开发守卫规范
1.  **禁越红线**：模块间通讯必须通过 `-api` 契约，严禁直接依赖 `-biz` 实现。
2.  **数据防腐**：禁止将 `Entity` 透传至 Controller 层，必须完成 `DTO/VO` 的转换清洗。
3.  **动态路由**：利用 `@DataSource` 注解实现多数据源的透明切换。


