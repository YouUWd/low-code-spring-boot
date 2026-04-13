# JDEC Platform 🚀

欢迎来到 **JDEC Platform**！目前本项目已演进为一个严谨的 **企业级 Maven 多模块化单体架构 (Modular Monolith)**。基于 Spring Boot 3 & Spring Modulith 构建，兼顾单机调试的便利性与未来零成本微服务拆分的能力。

---

## 🏗️ 真实项目结构与代码大纲

通过最新一轮的强约束重构，项目的业务代码被物理隔离在 `6` 个不同的 Maven Module 下。以下是当前工程真实的骨架结构（具体到代码类文件级别）：

```text
jdec-platform
│
├── jdec-platform-app/               # 🚀 启动组装中心
│   └── src/main/java/com/jdec/platform/
│       └── PlatformApplication.java # Spring Boot 主启动类
│
├── jdec-platform-shared/            # 🛠️ 公共基础设施 (所有依赖的底座)
│   └── src/main/java/com/jdec/platform/shared/
│       ├── config/                  # MyBatis-Plus / Redis 配置
│       ├── datasource/              # 动态多数据源配置与 AOP 路由切面
│       ├── exception/               # 全局异常处理定义
│       ├── model/                   # 公用基础模型 (ApiResponse, BaseEntity)
│       └── security/                # 安全过滤拦截器 (JwtAuthenticationFilter)
│
├── jdec-platform-auth-api/          # 🛂 认证域契约层 (只读轻量契约)
│   └── src/main/java/com/jdec/platform/auth/api/
│       ├── AuthApi.java             # 对外暴露的认证接口 (门面)
│       └── AuthEvents.java          # 跨域发布的内部通讯领域事件
│
├── jdec-platform-auth-biz/          # 🛂 认证域实现层 (核心逻辑)
│   └── src/main/java/com/jdec/platform/auth/
│       ├── controller/              # Web 路由接入层
│       ├── entity/                  # 数据库表映射对象
│       ├── mapper/                  # 数据访问层
│       └── service/                 # 业务逻辑实现
│
├── jdec-platform-config-api/        # ⚙️ 配置中心契约层
│   └── src/main/java/com/jdec/platform/configcenter/api/
│       ├── ConfigApi.java
│       └── ConfigEvents.java
│
└── jdec-platform-config-biz/        # ⚙️ 配置中心实现层
    └── src/main/java/com/jdec/platform/configcenter/
        ├── controller/
        ├── entity/
        ├── mapper/
        └── service/
```

### 🚨 绝对开发红线规范
1. **防腐层依赖隔离**：如果你在 `config-biz` 写代码，需要获取 User 信息，**只能**在 `pom.xml` 中引入 `jdec-platform-auth-api`。绝不允许跨域拉取 `-biz` 包，严禁循环依赖。
2. **Entity 清洗要求**：`Entity` 仅允许停留在 biz 层流转（如 `User.java`），禁止穿越通过 Controller 直接暴露出去。需要返出的值必须转换为 DTO/VO。

---

## 💻 快速开始

### 1. 本地环境初始化
- **基础环境**: JDK 21、MySQL 8.x、Redis
- **无需安装 Maven**：项目内置了 Maven Wrapper（`mvnw`），首次运行时会自动下载 Maven **3.9.13**，保证所有人的构建环境完全一致。
- **数据库准备**: 本地新建 `jdec_platform` 库 (UTF-8)。
- **配置修改**: 修改 `jdec-platform-app/src/main/resources/application-dev.yml` 中的 MySQL 和 Redis 密码。

### 2. 构建与运行

> **重要**：请始终使用 `./mvnw`（Linux/macOS）或 `mvnw.cmd`（Windows）代替 `mvn`，确保团队构建环境一致。

**⚠️ 首次下载后，务必在项目根目录进行全局编译，让 IDE 识别各子模块依赖关联：**
```bash
# Linux / macOS
./mvnw clean install -DskipTests

# Windows
.\mvnw.cmd clean install -DskipTests
```

启动应用（默认端口 8080，可通过 `--server.port=端口号` 指定）：
```bash
# Linux / macOS
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -pl jdec-platform-app

# Windows
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev -pl jdec-platform-app

# 自定义端口示例
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments="--server.port=8088" -pl jdec-platform-app
```

### 3. Curl 快速接口验证 (附带实战鉴权)
应用在 `8080` 端口启动后，可以通过以下命令快速测试工程连通性及 **无状态 JWT 鉴权体系**：

**① 快速注册一个新账号：**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin&password=123456&email=admin@jdec.com"
```

**② 账号密码登录 (获取 JWT Token)：**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin&password=123456"
```
*(注意：请复制并提取出 `data.token` 属性下返回的那长串字符串)*

**③ 携带 Token 访问受限接口 (新增一条配置参数)：**
```bash
curl -X POST http://localhost:8080/api/config \
  -H "Authorization: Bearer <填入上一步获取到的Token>" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "configKey=SYS_NAME&configValue=JDEC_PLAT&module=CORE&description=System_Name"
```

**④ 匿名查询刚才写入的配置：**
```bash
curl -X GET http://localhost:8080/api/config/SYS_NAME \
-H "Authorization: Bearer <填入上一步获取到的Token>"
```

---

## 🌐 核心 API 接口指引

为了统一前后端通讯与降低沟通成本，平台所有的接口请求传参默认规范为 `application/x-www-form-urlencoded`（除非有复杂的批量数据，否则尽量避免拼装 JSON Payload），并且所有的返回值外层全部被自动包裹在了安全的 `ApiResponse<T>` 中。

### 1. 身份认证中心 (`/api/auth`)

| 动词 | 路由路径 | 主要作用 | 许可级别 | 核心传递参数 | 响应泛型 `T` |
|:---:|---|---|---|---|---|
| **POST** | `/register` | 注册新用户 | 🔓 公开 | `username`, `password`, `email` | `UserInfo` |
| **POST** | `/login` | 账号密码签发登录 | 🔓 公开 | `username`, `password` | `LoginResult` (含 Token) |
| **GET** | `/users/{id}` | 按 ID 检索当前人员 | 🔒 Bearer Token | *[路径参数] id* | `UserInfo` |

### 2. 平台配置中心 (`/api/config`)

| 动词 | 路由路径 | 主要作用 | 许可级别 | 核心传递参数 | 响应泛型 `T` |
|:---:|---|---|---|---|---|
| **GET** | `/{key}` | 精确提取某个配置值 | 🔒 Bearer Token | *[路径参数] key* | `String` (对应的值) |
| **GET** | `/module/{module}` | 拉取某业务线的配置集 | 🔒 Bearer Token | *[路径参数] module* | `List<ConfigEntry>` |
| **POST** | `/` | 增量初始化一条配置 | 🔒 Bearer Token | `configKey`, `configValue`, `module` | `ConfigEntry` |
| **PUT** | `/{key}` | 修改并热更新配置值 | 🔒 Bearer Token | *[路径参数] key*, `configValue` | `ConfigEntry` |


---

## 🛡️ 代码提交约束与格式化 (Spotless)

本团队使用 **Spotless**（基于 Google Java Format / AOSP 4 空格缩进风格）进行自动化代码格式检查，并在 `.githooks/pre-commit` 中设置了强制卡点拦截，保障大型项目协作排版 100% 一致。

- **规范母本**：遵循 Google Java Format（AOSP 风格，4 空格缩进）。
- **拦截能力**：在执行 `git commit` 时，底层拦截脚本会自动扫描你的代码。如果发现多余导入 (`unused import`)，排版花里胡哨，或使用了 `import java.util.*` 等，终端会飘红并拒绝入库。

**📌 救星指令：一键修复全部风格警告**
```bash
# 日常开发请务必常常敲击这个命令自动格式化代码
./mvnw spotless:apply          # Linux / macOS
.\mvnw.cmd spotless:apply      # Windows
```
*(推荐安装 IntelliJ IDEA 的 Save Actions 官方插件实现保存即时格式化。)*

---

## 🌿 Git 分支与协同规范

为了保证团队高效协同、减少代码冲突，本项目采用以下标准的 Git 分支管理规范：

1. **主分支 (`master`)**：
   - **定位**：生产环境稳定版本，永远处于“可发布”状态。
   - **规则**：严禁直接 Push 提交，必须通过 Merge Request (MR) 且经过 Review 后合并。

2. **开发主分支 (`develop`)**：
   - **定位**：日常的主干测试分支，包含了下个版本待发布的所有最新功能。
   - **规则**：从 `master` 切出。所有的新功能开发和常规 Bug 修复，最终必须合并回此分支。

3. **功能特性分支 (`feature/<功能名>`)**：
   - **定位**：用于开发单个独立的新业务功能或模块。
   - **操作**：从 `develop` 分支切出（如 `feature/multi-datasource`），开发自测完毕后，发起 MR 合并回 `develop`。

4. **热修复分支 (`hotfix/<问题单号_简述>`)**：
   - **定位**：用于紧急修复线上 `master` 分支暴露出的严重 Bug。
   - **操作**：直接从 `master` 切出，紧急修复并测试通过后，必须**同时合并回** `master` （发版）和 `develop` （同步修复至测试环境）。

5. **常规修复分支 (`bugfix/<问题单号_简述>`)**：
   - **定位**：用于修复测试环境发现的常规 Bug。
   - **操作**：从 `develop` 切出，修复后合并回 `develop`。

---

## ✅ 架构防腐与测试边界验证

作为 Spring Modulith 的亮点，你在编写完一个新功能包时，推荐执行架构自守卫程序：
```bash
./mvnw test -pl jdec-platform-app -Dtest=ModularityTests
```
一旦你在包中发生了“跨域越权私自调取底层代码”，这个单元测试将直接以红字打回该编译，并且指点出违规的行号，逼迫你回到 `XxxApi` 完成合理封装。

---

## 💡 落地代码实战示例

**Q: 在 `AuthServiceImpl` 中需要拿到配置中心参数该怎么写？**

```java
package com.jdec.platform.auth.service;

import com.jdec.platform.configcenter.api.ConfigApi; // 🟢 只依赖 API 契约类

@Service
@RequiredArgsConstructor
public class AuthServiceImpl {
    private final ConfigApi configApi;

    public void doLogin() {
        // 完成微服务级解耦：跨服务安全调用，不需要把 config-biz 当工程引进来
        String timeout = configApi.getConfigValue("LOGIN_TIMEOUT"); 
    }
}
```

**示例 2：Entity 为什么不能直接扔给前端？**

为了防止数据库结构及敏感字段（如逻辑删除标识、密码）被意外泄露，在业务层必须做防腐和数据封装清洗：

```java
// ❌ 错误做法 (直接返回 Entity，破坏了防腐层)
@GetMapping("/{id}")
public ApiResponse<UserEntity> getUser(@PathVariable Long id) {
    return ApiResponse.success(userMapper.selectById(id));
}

// 🟢 正确做法 (在 biz 组装成 API 层的只读 DTO 再返回出去)
@GetMapping("/{id}")
public ApiResponse<UserInfoDTO> getUser(@PathVariable Long id) {
    UserEntity user = userMapper.selectById(id);
    UserInfoDTO dto = new UserInfoDTO(user.getId(), user.getUsername(), user.getEmail());
    return ApiResponse.success(dto);
}
```

**示例 3：如何一行代码动态切换多数据源？ (@DataSource)**

平台目前配置了主库 (`jdec_platform`) 并且集成了强大的自定义 AOP 数据源路由机制（支持 `coal-industry` 与时序库 `TDengine` 等）。
如果在某个开发场景下你需要查主表又要查副表，你只需要在一个方法上贴一个注解即可，完全无侵入：

```java
package com.jdec.platform.auth.service;

import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;

@Service
@RequiredArgsConstructor
public class ExternalDataService {
    
    // 假设这是一个查询副表 mapper
    private final CoalMapper coalMapper;

    // 🟢 贴上 @DataSource 注解，这整个方法执行时，就会自动切入到煤炭子数据库处理！
    @DataSource(DataSourceConstants.COAL_INDUSTRY)
    public List<CoalData> fetchLatestCoalData() {
        return coalMapper.selectList(null);
    }

    // 默认不贴注解的方法，永远只会走 jdec_platform 主库
    public void doAuth() { ... }
}
```

---

> 🎉 **写在最后**
>
> 遇到任何框架级问题或全局性工具缺失，请优先补充到 `jdec-platform-shared` 模块中；切忌不要在各自前后独立的高内聚 BIZ 模块中重复造轮子。祝您开发愉快！
