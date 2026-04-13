# JDEC Platform 开发文档

## 📋 项目概览

**JDEC Platform** 是一个基于 Spring Boot 3.5.11 和 Spring Modulith 1.4.10 的企业级模块化单体应用。采用严格的模块化架构，通过 Maven 模块边界实现高内聚、低耦合的系统设计。

- **项目版本**: 1.0.0-SNAPSHOT
- **Java版本**: JDK 21
- **架构模式**: 模块化单体（Modular Monolith）
- **主要框架**: Spring Boot + Spring Modulith + MyBatis-Plus

---

## 🏗️ 系统架构

### 模块划分

项目采用**模块-API-业务**三层结构，确保模块间的清晰边界：

```
jdec-platform/
├── jdec-platform-shared/          # 共享基础设施层
│   ├── security/                  # JWT认证、安全配置
│   ├── datasource/                # 多数据源管理
│   ├── model/                     # 统一响应、异常定义
│   └── config/                    # 全局配置
│
├── jdec-platform-auth/            # 认证模块
│   ├── auth-api/                  # 暴露的API接口
│   │   └── SysUserApi             # 验证码、登录接口
│   └── auth-biz/                  # 业务实现
│       ├── service/               # AuthServiceImpl、VerificationCodeService
│       ├── entity/                # SysUser
│       └── mapper/                # SysUserMapper
│
├── jdec-platform-config/          # 配置中心模块
│   ├── config-api/                # 暴露的API接口
│   │   ├── SysModuleFieldConfigApi
│   │   └── SysUserFieldConfigApi
│   └── config-biz/                # 业务实现
│
├── jdec-platform-hr/              # 人力资源模块
│   ├── hr-api/                    # 暴露的API接口
│   │   └── UserApi                # 用户查询、创建、更新
│   └── hr-biz/                    # 业务实现
│       ├── service/               # UserService
│       ├── entity/                # User、Department
│       └── mapper/                # UserMapper
│
└── jdec-platform-app/             # 应用入口
    ├── PlatformApplication        # 启动类
    ├── controller/                # LoginController
    └── resources/                 # 配置文件
```

### 架构约束（Redlines）

模块化架构通过以下约束确保系统的可维护性：

| 约束 | 说明 |
|------|------|
| ✅ 模块间通信 | 只能通过 `-api` 接口，禁止直接依赖 `-biz` |
| ✅ 数据隔离 | Entity 类限制在 biz 层，外部使用 DTO/VO |
| ✅ 包隐藏 | 禁止访问模块内部包（如 service、mapper） |
| ✅ 边界验证 | ModularityTests 自动验证模块边界完整性 |

---

## 💾 数据库设计

### 多数据源架构

系统支持多个独立的数据源，每个模块对应一个数据库：

| 数据源 | 模块 | 主要表 | 用途 |
|------|------|-------|------|
| **auth_center** | auth | sys_user | 用户认证 |
| **config_center** | config | sys_module, sys_module_field_config, sys_user_field_config, sys_role, sys_module_permission | 配置管理、权限定义 |
| **hr_manage** | hr | user, department, organization, depart_manage, sys_approval_chain | 人力资源管理 |
| **third** | - | - | 第三方数据 |

### 关键表结构

#### auth_center.sys_user - 用户认证表

```sql
CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64),              -- 用户名
  password VARCHAR(255),             -- 密码（加密）
  email VARCHAR(128),                -- 邮箱
  phone VARCHAR(32),                 -- 手机号
  status TINYINT DEFAULT 1,          -- 状态（1:启用, 0:禁用）
  subject_id TINYINT,                -- 主体ID（关联企业微信）
  subject_name VARCHAR(20),          -- 主体简称
  created_by INT,                    -- 创建人
  created_date INT,                  -- 创建时间
  updated_by INT,                    -- 更新人
  updated_date INT,                  -- 更新时间
  is_delete INT DEFAULT 1            -- 删除标记
);
```

#### config_center.sys_module - 模块定义表

```sql
CREATE TABLE sys_module (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  module_code VARCHAR(32) UNIQUE,    -- 模块编码
  module_name VARCHAR(64),           -- 模块名称
  description VARCHAR(255),          -- 模块描述
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### config_center.sys_module_field_config - 字段配置表

```sql
CREATE TABLE sys_module_field_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  module_id BIGINT,                  -- 模块ID
  field_code VARCHAR(64),            -- 字段编码
  field_name VARCHAR(100),           -- 字段中文名称
  field_type VARCHAR(32),            -- 字段类型
  visible TINYINT DEFAULT 1,         -- 是否可见
  sort_order INT DEFAULT 0,          -- 展示顺序
  width INT,                         -- 字段宽度
  align VARCHAR(32) DEFAULT 'left',  -- 对齐方式
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_module_field (module_id, field_code),
  FOREIGN KEY (module_id) REFERENCES sys_module(id) ON DELETE CASCADE
);
```

#### hr_manage.user - 员工信息表

```sql
CREATE TABLE user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  userName VARCHAR(64),              -- 用户名
  nickName VARCHAR(64),              -- 昵称
  userAvatar VARCHAR(255),           -- 头像URL
  sex VARCHAR(10),                   -- 性别
  subjectId INT,                     -- 主体ID
  shortName VARCHAR(64),             -- 企业微信short_name
  workNumber VARCHAR(32),            -- 工号
  phone VARCHAR(32),                 -- 手机号
  departmentId BIGINT,               -- 部门ID
  departmentName VARCHAR(100),       -- 部门名称
  performanceType VARCHAR(32),       -- 绩效类型
  userStatus VARCHAR(32),            -- 用户状态
  jobStatus VARCHAR(32),             -- 岗位状态
  created_date DATETIME,
  updated_date DATETIME
);
```

---

## 🔌 API 接口设计

### 认证模块 (auth-api)

#### SysUserApi 接口

```java
public interface SysUserApi {
    /**
     * 发送验证码
     * @param phone 手机号
     * @param subjectId 主体ID
     * @param weChatUserId 企业微信用户ID
     */
    void sendCode(String phone, Integer subjectId, String weChatUserId);

    /**
     * 验证码登录
     * @param phone 手机号
     * @param code 验证码
     * @return 登录响应
     */
    LoginResp loginWithCode(String phone, String code);
}
```

**请求/响应示例**

```bash
# 发送验证码
POST /api/auth/code/send
Content-Type: application/json

{
  "phone": "13800138000"
}

# 响应
{
  "status": 200,
  "msg": "ok",
  "data": "验证码已发送"
}
```

```bash
# 登录
POST /api/auth/login
Content-Type: application/json

{
  "phone": "13800138000",
  "code": "123456",
  "includeEmployeeInfo": true
}

# 响应
{
  "status": 200,
  "msg": "ok",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": 1,
    "username": "admin",
    "email": "admin@jdec.com",
    "status": 1,
    "employeeInfo": {
      "id": 1,
      "userName": "admin",
      "phone": "13800138000",
      "subjectId": 3,
      "shortName": "admin",
      "workNumber": "001",
      "departmentName": "技术部",
      "userStatus": "active"
    }
  }
}
```

### 人力资源模块 (hr-api)

#### UserApi 接口

```java
public interface UserApi {
    /**
     * 根据手机号获取用户的主体ID
     */
    Optional<Integer> getSubjectIdByPhone(String phone);

    /**
     * 根据手机号获取企业微信用户ID
     */
    Optional<String> getWeChatUserIdByPhone(String phone);

    /**
     * 根据手机号获取用户基本信息
     */
    Optional<UserBasicInfoResp> getUserBasicInfoByPhone(String phone);

    /**
     * 创建用户
     */
    UserBasicInfoResp createUser(CreateUserReq request);

    /**
     * 更新用户
     */
    UserBasicInfoResp updateUser(UpdateUserReq request);
}
```

### 配置中心模块 (config-api)

#### SysModuleFieldConfigApi 接口

```java
public interface SysModuleFieldConfigApi {
    /**
     * 获取模块的字段配置
     */
    List<ModuleFieldConfigResp> getModuleFieldConfigs(String moduleCode);

    /**
     * 添加字段配置
     */
    ModuleFieldConfigResp addFieldConfig(String moduleCode, ModuleFieldConfigReq request);

    /**
     * 更新字段配置
     */
    ModuleFieldConfigResp updateFieldConfig(String moduleCode, String fieldCode, ModuleFieldConfigReq request);

    /**
     * 删除字段配置
     */
    void deleteFieldConfig(String moduleCode, String fieldCode);

    /**
     * 批量更新字段配置
     */
    List<ModuleFieldConfigResp> updateFieldConfigs(String moduleCode, List<ModuleFieldConfigReq> requests);
}
```

---

## 🔐 核心业务逻辑

### 1. 验证码登录流程

```
┌─────────────────────────────────────────────────────────────┐
│ 客户端                                                        │
└─────────────────────────────────────────────────────────────┘
         │
         │ 1. POST /api/auth/code/send
         ▼
┌─────────────────────────────────────────────────────────────┐
│ LoginController.sendCode()                                   │
│ ├─ 从 hr.api 获取用户的 subjectId                            │
│ ├─ 从 hr.api 获取用户的企业微信ID                            │
│ └─ 调用 auth.api.sendCode()                                  │
└─────────────────────────────────────────────────────────────┘
         │
         │ 2. VerificationCodeService.sendCode()
         ▼
┌─────────────────────────────────────────────────────────────┐
│ 企业微信 API                                                  │
│ └─ 发送验证码消息                                            │
└─────────────────────────────────────────────────────────────┘
         │
         │ 3. Redis 存储验证码（5分钟过期）
         ▼
┌─────────────────────────────────────────────────────────────┐
│ 客户端收到验证码                                              │
└─────────────────────────────────────────────────────────────┘
         │
         │ 4. POST /api/auth/login
         ▼
┌─────────────────────────────────────────────────────────────┐
│ LoginController.login()                                      │
│ ├─ 调用 auth.api.loginWithCode()                             │
│ │  ├─ 验证验证码（最多5次尝试）                              │
│ │  ├─ 查询 sys_user 获取用户信息                             │
│ │  ├─ 检查账户状态                                          │
│ │  └─ 生成 JWT Token                                        │
│ ├─ 如果 includeEmployeeInfo=true                             │
│ │  └─ 从 hr.api 获取员工详细信息                             │
│ └─ 返回统一登录响应                                          │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│ 客户端获得 Token 和用户信息                                   │
└─────────────────────────────────────────────────────────────┘
```

### 2. 多数据源管理

系统使用 `@DataSource` 注解实现动态数据源切换：

```java
@Service
@DataSource(DataSourceConstants.AUTH_CENTER)
public class AuthServiceImpl implements SysUserApi {
    // 自动使用 auth_center 数据源
}

@Service
@DataSource(DataSourceConstants.HR_MANAGE)
public class UserService implements UserApi {
    // 自动使用 hr_manage 数据源
}
```

**工作原理**：
1. `@DataSource` 注解标记目标数据源
2. `DataSourceAspect` 拦截器在方法执行前切换数据源
3. 数据源信息存储在 `ThreadLocal` 中
4. MyBatis 执行时使用当前线程的数据源
5. 方法执行后自动恢复默认数据源

### 3. JWT 认证机制

```java
public class JwtTokenProvider {
    /**
     * 生成 JWT Token
     * @param userId 用户ID
     * @param username 用户名
     * @param extra 额外声明（可选）
     * @return JWT 字符串
     */
    public String generateToken(Long userId, String username, Map<String, Object> extra);

    /**
     * 解析 JWT Token
     * @param token JWT 字符串
     * @return Claims 或 null
     */
    public Claims parseToken(String token);

    /**
     * 验证 Token 有效性
     */
    public boolean validateToken(String token);
}
```

**Token 结构**：
```
Header: {
  "alg": "HS256",
  "typ": "JWT"
}

Payload: {
  "sub": "1",                    // 用户ID
  "username": "admin",           // 用户名
  "iat": 1704067200,            // 签发时间
  "exp": 1704153600             // 过期时间（24小时）
}

Signature: HMACSHA256(base64UrlEncode(header) + "." + base64UrlEncode(payload), secret)
```

### 4. 字段个性化配置

系统支持两层字段配置：

**模块级配置** - 定义模块的标准字段
```
sys_module_field_config
├─ module_id: 模块ID
├─ field_code: 字段编码
├─ field_name: 字段中文名称
├─ field_type: 字段类型
├─ visible: 是否可见
├─ sort_order: 展示顺序
└─ width: 字段宽度
```

**用户级配置** - 用户可自定义字段展示
```
sys_user_field_config
├─ user_id: 用户ID
├─ module_id: 模块ID
├─ field_code: 字段编码
├─ visible: 用户是否可见
└─ sort_order: 用户自定义顺序
```

---

## 🛠️ 技术栈

### 核心依赖

| 组件 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.5.11 | 应用框架 |
| Spring Modulith | 1.4.10 | 模块化架构 |
| MyBatis-Plus | 3.5.16 | ORM 框架 |
| MySQL Connector | 最新 | 数据库驱动 |
| Redis | - | 缓存、验证码存储 |
| JJWT | 0.12.6 | JWT 令牌 |
| SpringDoc OpenAPI | 2.8.5 | API 文档 |
| Jackson | 2.18.2 | JSON 序列化 |
| Lombok | 1.18.32 | 代码生成 |

### 基础设施

- **多数据源**: 动态数据源切换（DataSourceAspect）
- **缓存**: Redis（Lettuce 连接池）
- **安全**: Spring Security + JWT
- **AOP**: 数据源切换、日志记录
- **API 文档**: Swagger UI + OpenAPI 3.0
- **代码格式**: Spotless（Google Java Style）

---

## 🚀 开发指南

### 环境配置

#### 开发环境 (application-dev.yml)

```yaml
spring:
  datasource:
    dynamic:
      datasources:
        auth_center:
          url: jdbc:mysql://192.168.60.6:5506/auth_center
          username: root
          password: mysql-platform
        config_center:
          url: jdbc:mysql://192.168.60.6:5506/config_center
          username: root
          password: mysql-platform
        hr_manage:
          url: jdbc:mysql://192.168.60.6:5506/hr_manage
          username: root
          password: mysql-platform
  data:
    redis:
      host: 192.168.60.6
      port: 5379
      password: redis-jd-platform

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # SQL 日志输出

logging:
  level:
    com.jdec.platform: DEBUG
```

#### 企业微信配置

```yaml
wechat:
  work:
    subjects:
      environment:
        corp-id: wxa1d00a1a588baec3
        corp-secret: ${WECHAT_ENVIRONMENT_SECRET}
        agent-id: 1000015
      electric:
        corp-id: wxa1d00a1a588baec3
        corp-secret: ${WECHAT_ELECTRIC_SECRET}
        agent-id: 1000015
      energy:
        corp-id: wwa69aae03b2f90d72
        corp-secret: ${WECHAT_ENERGY_SECRET}
        agent-id: 1000002
```

### 常用命令

```bash
# 构建项目
mvn clean install -DskipTests

# 运行应用
mvn spring-boot:run -pl jdec-platform-app

# 运行测试
mvn test

# 验证模块边界
mvn test -pl jdec-platform-app -Dtest=ModularityTests

# 代码格式化
mvn spotless:apply

# 检查代码格式
mvn spotless:check
```

### 创建新模块

1. **创建模块目录结构**
   ```
   jdec-platform-newmodule/
   ├── pom.xml
   ├── newmodule-api/
   │   └── src/main/java/com/jdec/platform/newmodule/api/
   │       └── NewModuleApi.java
   └── newmodule-biz/
       └── src/main/java/com/jdec/platform/newmodule/biz/
           ├── service/
           ├── entity/
           └── mapper/
   ```

2. **编写 pom.xml**
   ```xml
   <parent>
     <groupId>com.jdec</groupId>
     <artifactId>jdec-platform</artifactId>
     <version>1.0.0-SNAPSHOT</version>
   </parent>

   <artifactId>jdec-platform-newmodule-api</artifactId>
   <name>JDEC Platform NewModule API</name>
   ```

3. **定义 API 接口**
   ```java
   public interface NewModuleApi {
       // 定义模块暴露的接口
   }
   ```

4. **实现业务逻辑**
   ```java
   @Service
   @DataSource(DataSourceConstants.NEW_MODULE)
   public class NewModuleServiceImpl implements NewModuleApi {
       // 实现接口
   }
   ```

5. **在主 pom.xml 中添加模块**
   ```xml
   <modules>
       <module>jdec-platform-newmodule-api</module>
       <module>jdec-platform-newmodule-biz</module>
   </modules>
   ```

### 模块间通信示例

```java
@RestController
@RequestMapping("/api/example")
@RequiredArgsConstructor
public class ExampleController {
    
    private final UserApi userApi;           // 注入 hr 模块 API
    private final SysUserApi authApi;        // 注入 auth 模块 API
    private final SysModuleFieldConfigApi configApi;  // 注入 config 模块 API

    @GetMapping("/user/{phone}")
    public ApiResponse<UserBasicInfoResp> getUser(@PathVariable String phone) {
        // 通过 API 接口调用其他模块
        return ApiResponse.success(
            userApi.getUserBasicInfoByPhone(phone).orElse(null)
        );
    }
}
```

### 异常处理

```java
// 自定义业务异常
throw new BusinessException(404, "用户不存在");
throw new BusinessException(401, "验证码错误");
throw new BusinessException(403, "账户已被禁用");

// 全局异常处理器会自动转换为 ApiResponse
{
  "status": 404,
  "msg": "用户不存在",
  "data": null
}
```

---

## 📊 API 响应格式

所有 API 响应都遵循统一的格式：

```java
public class ApiResponse<T> {
    private int status;      // HTTP 状态码
    private String msg;      // 响应消息
    private T data;          // 响应数据
}
```

**成功响应**
```json
{
  "status": 200,
  "msg": "ok",
  "data": { ... }
}
```

**错误响应**
```json
{
  "status": 400,
  "msg": "参数错误",
  "data": null
}
```

**工厂方法**
```java
ApiResponse.success(data);                    // 成功响应
ApiResponse.success("自定义消息", data);      // 自定义消息
ApiResponse.error(400, "参数错误");           // 错误响应
```

---

## 🔍 调试技巧

### 1. 启用 SQL 日志

在 `application-dev.yml` 中配置：
```yaml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

### 2. 查看数据源切换

在 `DataSourceAspect` 中添加日志：
```java
log.info("切换数据源: {}", dataSourceName);
```

### 3. 验证 JWT Token

使用在线工具 [jwt.io](https://jwt.io) 解析 Token，查看 Payload 内容。

### 4. 测试 API

使用 Postman 或 curl 测试 API：
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "13800138000",
    "code": "123456",
    "includeEmployeeInfo": true
  }'
```

---

## 📝 开发规范

### REST API 设计

- **路径**: `/api/{domain}/{resource}`
- **方法**: GET（查询）、POST（创建）、PUT（更新）、DELETE（删除）
- **响应**: 统一使用 `ApiResponse<T>` 封装

### 代码风格

- **代码格式**: Google Java Style（通过 Spotless 自动格式化）
- **命名规范**: 
  - 类名: PascalCase
  - 方法名: camelCase
  - 常量: UPPER_SNAKE_CASE
- **注释**: 使用 JavaDoc 注释公共方法

### 模块边界

- ✅ 只能通过 `-api` 接口通信
- ✅ 禁止直接依赖 `-biz` 模块
- ✅ Entity 类限制在 biz 层
- ✅ 外部通信使用 DTO/VO

### 数据库规范

- **表名**: snake_case（如 `sys_user`）
- **字段名**: snake_case（如 `user_name`）
- **主键**: `id` BIGINT AUTO_INCREMENT
- **时间字段**: `created_date`、`updated_date`
- **删除标记**: `is_delete`（1:显示, 0:隐藏）

---

## 🔗 相关文档

- [Spring Modulith 官方文档](https://spring.io/projects/spring-modulith)
- [MyBatis-Plus 官方文档](https://baomidou.com/)
- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)
- [JWT 官方文档](https://jwt.io/)

---

## 📞 常见问题

### Q: 如何添加新的数据源？

A: 在 `application-dev.yml` 中添加数据源配置，然后在 `DataSourceConstants` 中定义常量，最后在 Service 类上使用 `@DataSource` 注解。

### Q: 如何跨模块调用其他模块的功能？

A: 通过注入模块的 `-api` 接口，调用其暴露的方法。禁止直接依赖 `-biz` 模块。

### Q: 如何验证模块边界是否正确？

A: 运行 `mvn test -pl jdec-platform-app -Dtest=ModularityTests` 命令验证。

### Q: 如何处理跨模块的事务？

A: 在应用层（app 模块）的 Controller 中处理事务，通过调用各模块的 API 完成业务逻辑。

### Q: 如何添加新的权限？

A: 在 `config_center` 数据库的 `sys_module_permission` 表中添加权限定义，然后在 `sys_role_permission` 表中关联角色。

---

**文档版本**: 1.0.0  
**最后更新**: 2026-04-13  
**维护者**: JDEC Platform Team
