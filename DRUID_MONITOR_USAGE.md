# Druid 监控页面使用说明

## 已完成的配置

### 1. 依赖管理
- 父 `pom.xml`：添加 Druid 1.2.23 版本管理
- `jdec-platform-shared` 模块：添加 `druid-spring-boot-3-starter` 依赖

### 2. 数据源配置
- **DynamicDataSourceConfig.java**：将 HikariCP 替换为 DruidDataSource
- 配置了连接池参数、监控统计、慢 SQL 记录（超过 2 秒）
- 启用了 `stat`（监控统计）、`wall`（SQL 防火墙）、`slf4j`（日志）过滤器

### 3. 监控页面配置
- **DruidMonitorConfig.java**：配置 Druid 监控 Servlet 和 Web 统计过滤器
- **application-dev.yml**：配置 Druid 监控参数

## 访问 Druid 监控页面

### 1. 启动应用
```bash
mvn spring-boot:run
```

### 2. 访问监控页面
```
URL: http://localhost:8080/druid
用户名: admin
密码: admin123
```

## 监控页面功能

### 1. 数据源页面
- 查看所有数据源的连接池状态
- 实时监控连接数、活跃连接、空闲连接
- 查看数据源配置信息

### 2. SQL 监控（重点功能）
**这是最接近 PHP 调试工具的功能！**

可以看到：
- **执行的 SQL 语句**：完整的 SQL（参数已替换）
- **执行次数**：每条 SQL 执行了多少次
- **执行时间**：最慢、最快、平均执行时间
- **读取行数**：SQL 返回的行数
- **更新行数**：SQL 影响的行数
- **执行堆栈**：点击 SQL 可以看到是哪个 Java 方法调用的（可以追踪到 Controller）

**示例：**
```
SQL: SELECT id, name, age FROM user WHERE id = ?
执行次数: 15
最慢: 125ms
最快: 5ms
平均: 12ms
```

点击 SQL 语句可以看到：
```
调用堆栈:
com.jdec.platform.config.biz.controller.UserController.getUser(UserController.java:45)
com.jdec.platform.config.biz.service.UserService.getById(UserService.java:23)
com.baomidou.mybatisplus.core.mapper.BaseMapper.selectById(BaseMapper.java:15)
```

### 3. SQL 防火墙
- 检测 SQL 注入攻击
- 统计被拦截的危险 SQL
- 查看黑名单规则

### 4. Web 应用
- 监控所有 HTTP 请求
- 查看每个接口的访问次数、响应时间
- **可以看到每个接口执行了哪些 SQL**（关键功能）

### 5. URI 监控
- 按 URI 统计请求次数
- 查看每个接口的平均响应时间
- 慢接口统计

### 6. Session 监控
- 查看当前活跃的 Session
- Session 创建和销毁统计

### 7. Spring 监控
- 监控 Spring Bean 的方法调用
- 查看方法执行时间

## 如何通过页面查询接口执行的 SQL

### 方法 1：通过 Web 应用页面（推荐）

1. 访问 `http://localhost:8080/druid`
2. 点击左侧菜单 **"Web 应用"**
3. 找到你要查询的接口（例如：`/api/user/list`）
4. 点击该接口，可以看到：
   - 请求次数
   - 平均响应时间
   - **执行的 SQL 列表**（包括每条 SQL 的执行次数和时间）

### 方法 2：通过 SQL 监控页面

1. 访问 `http://localhost:8080/druid`
2. 点击左侧菜单 **"SQL 监控"**
3. 查看所有执行过的 SQL
4. 点击某条 SQL，可以看到：
   - **调用堆栈**：显示是哪个 Controller、Service、Mapper 调用的
   - 执行时间分布
   - 执行次数统计

### 方法 3：实时监控

1. 打开 Druid 监控页面
2. 在浏览器中访问你的接口（例如：`http://localhost:8080/api/user/list`）
3. 刷新 Druid 监控页面，立即可以看到刚才执行的 SQL

## 慢 SQL 监控

### 配置
已配置慢 SQL 阈值为 **2 秒**，超过 2 秒的 SQL 会被标记为慢 SQL。

### 查看慢 SQL
1. 访问 `http://localhost:8080/druid`
2. 点击 **"SQL 监控"**
3. 点击 **"慢"** 列排序，查看最慢的 SQL
4. 慢 SQL 会用红色标记

### 调整慢 SQL 阈值
编辑 `DynamicDataSourceConfig.java`：
```java
// 慢 SQL 记录（超过 1 秒）
ds.setConnectionProperties("druid.stat.mergeSql=true;druid.stat.slowSqlMillis=1000");
```

或编辑 `application-dev.yml`：
```yaml
spring:
  datasource:
    druid:
      filter:
        stat:
          slow-sql-millis: 1000  # 1 秒
```

## 控制台日志

除了 Web 页面，控制台也会输出 SQL 日志：

### MyBatis-Plus 日志（原始 SQL）
```
==>  Preparing: SELECT id, name, age FROM user WHERE id = ?
==> Parameters: 1(Integer)
<==      Total: 1
```

### Druid 日志（可执行 SQL）
```
2026-05-29 09:00:00 | SQL: SELECT id, name, age FROM user WHERE id = 1 | 执行时间: 15ms
```

## 生产环境配置

### 关闭监控页面
编辑 `application-prod.yml`：
```yaml
spring:
  datasource:
    druid:
      stat-view-servlet:
        enabled: false  # 关闭监控页面
      web-stat-filter:
        enabled: false  # 关闭 Web 统计
```

### 限制访问 IP
编辑 `DruidMonitorConfig.java`：
```java
// 只允许内网访问
registrationBean.addInitParameter("allow", "127.0.0.1,192.168.0.0/16");
```

### 修改登录密码
编辑 `DruidMonitorConfig.java`：
```java
registrationBean.addInitParameter("loginUsername", "your_username");
registrationBean.addInitParameter("loginPassword", "your_strong_password");
```

## 多数据源支持

Druid 已完美支持项目的 6 个动态数据源：
- primary
- auth_center
- config_center
- hr_manage
- third
- school

在监控页面可以分别查看每个数据源的：
- 连接池状态
- SQL 执行情况
- 慢 SQL 统计

## 常见问题

### 1. 看不到 SQL 监控数据
- 确保已经执行过接口请求
- 检查 `DynamicDataSourceConfig.java` 中是否配置了 `stat` 过滤器
- 查看控制台是否有错误日志

### 2. 监控页面无法访问
- 检查应用是否启动成功
- 确认访问地址：`http://localhost:8080/druid`
- 检查用户名密码：`admin` / `admin123`

### 3. 看不到调用堆栈
- 确保配置了 `druid.stat.mergeSql=true`
- 重启应用后再试

### 4. 性能影响
- **开发环境**：可以放心使用，性能影响小于 5%
- **生产环境**：建议关闭 Web 监控页面，保留 SQL 统计功能

## 对比 PHP 调试工具

| 功能 | PHP (Laravel Debugbar) | Druid 监控 |
|------|------------------------|-----------|
| 查看 SQL 语句 | ✅ | ✅ |
| SQL 执行时间 | ✅ | ✅ |
| SQL 执行次数 | ✅ | ✅ |
| 调用堆栈 | ✅ | ✅ |
| 慢 SQL 统计 | ✅ | ✅ |
| 实时监控 | ✅ | ✅ |
| 历史数据 | ❌ | ✅ |
| 多数据源支持 | ❌ | ✅ |
| SQL 防火墙 | ❌ | ✅ |

## 高级功能

### 1. SQL 合并
相同的 SQL（参数不同）会被合并统计：
```
SELECT * FROM user WHERE id = ?
执行次数: 100
```

### 2. SQL 执行时间分布
可以看到 SQL 执行时间的分布情况：
- 0-1ms: 50 次
- 1-10ms: 30 次
- 10-100ms: 15 次
- 100ms+: 5 次

### 3. 导出监控数据
点击页面右上角的 **"导出"** 按钮，可以导出 JSON 格式的监控数据。

### 4. 重置统计数据
点击页面右上角的 **"重置"** 按钮，可以清空所有统计数据（需要在配置中启用）。

## 参考资料
- Druid 官方文档：https://github.com/alibaba/druid/wiki
- Druid 监控配置：https://github.com/alibaba/druid/wiki/%E9%85%8D%E7%BD%AE_StatViewServlet%E9%85%8D%E7%BD%AE
- Druid SQL 监控：https://github.com/alibaba/druid/wiki/%E5%86%85%E7%BD%AESQL%E7%9B%91%E6%8E%A7
