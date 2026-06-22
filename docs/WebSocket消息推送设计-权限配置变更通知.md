# WebSocket 消息推送设计 - 权限配置变更通知

## 一、整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         系统配置（config-biz）                    │
│  模块/角色权限/角色配置变更 → 发布到 Redis（按 projectNo 路由）    │
└────────────────────────────┬────────────────────────────────────┘
                             │  Redis Pub/Sub
                             │  Channel: jdec:config:change:{subjectId}:{projectNo}
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    业务系统（hr-biz / 其他模块）                   │
│  订阅 Redis → 根据 changeType 处理 payload → WebSocket 推送前端   │
└─────────────────────────────────────────────────────────────────┘
                             │  WebSocket
                             ▼
                    ┌────────────────┐
                    │    前端用户     │
                    │  执行对应动作   │
                    └────────────────┘
```

**核心分工**：
- **config-biz**：只负责发布 Redis 消息，无 WebSocket
- **业务系统**：订阅 Redis 消息，根据 changeType 自行处理 payload，通过 WebSocket 推送前端
- **解耦方式**：通过 `subjectId + projectNo` 作为消息路由键，各业务系统只消费与自身相关的消息

---

## 二、推送范围（三大类）

| 类别 | 说明 | 典型场景 |
|------|------|----------|
| **模块变更** | 模块表（sys_module）变更 | 新增模块、修改模块配置、删除模块 |
| **角色权限变更** | 角色菜单、角色模块权限变更 | 管理员修改角色可访问的菜单、角色对模块的权限 |
| **角色配置变更** | 角色基本信息、角色状态变更 | 角色名称修改、角色启用/禁用、角色删除 |

---

## 三、Redis 消息格式

### 3.1 Channel 命名规则

```
jdec:config:change:{subjectId}:{projectNo}
```

**示例**：

- `jdec:config:change:1:HR001` - 主体1的HR项目
- `jdec:config:change:2:ENV001` - 主体2的环境项目

> **说明**：`subjectId` 和 `projectNo` 必须同时包含，保证多主体隔离和项目隔离

---

### 3.2 Redis 消息结构

```json
{
  "messageId": "msg_1718692800001",
  "publishTime": "2026-06-18T10:30:00.001+08:00",
  "subjectId": 1,
  "projectNo": "HR001",
  "changeType": "ROLE_MENU_CHANGED",
  "payload": {
    "roleId": 5
  },
  "operator": {
    "userId": 10000,
    "userName": "管理员张三"
  }
}
```

### 3.3 Redis 消息字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `messageId` | String | 是 | 消息唯一ID，格式 `msg_{timestamp}_{random}` |
| `publishTime` | String | 是 | 发布时间（ISO 8601） |
| `subjectId` | Long | 是 | 主体ID，多主体隔离 |
| `projectNo` | String | 是 | 项目编码，用于 channel 路由 |
| `changeType` | String | 是 | 变更类型（见下方详细说明） |
| `payload` | Object | 是 | 变更内容，业务系统根据 changeType 自行解析 |
| `operator` | Object | 否 | 操作人信息 |

---

## 四、changeType 与 payload 定义

### 4.1 模块变更（MODULE_CHANGED）

**触发场景**：
- 新增模块
- 修改模块配置（名称、状态等）
- 删除模块

**payload 结构**：

```json
{
  "moduleId": 20
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `moduleId` | Long | 是 | 变更的模块ID |

**完整消息示例**：

```json
{
  "messageId": "msg_1718692800001",
  "publishTime": "2026-06-18T10:30:00.001+08:00",
  "subjectId": 1,
  "projectNo": "HR001",
  "changeType": "MODULE_CHANGED",
  "payload": {
    "moduleId": 20
  },
  "operator": {
    "userId": 10000,
    "userName": "管理员张三"
  }
}
```

---

### 4.2 角色菜单变更（ROLE_MENU_CHANGED）

**触发场景**：

- 修改角色的菜单权限（sys_role_menu）

**payload 结构**：

```json
{
  "roleId": 5
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `roleId` | Long | 是 | 变更的角色ID |

**完整消息示例**：

```json
{
  "messageId": "msg_1718692800002",
  "publishTime": "2026-06-18T10:31:00.002+08:00",
  "subjectId": 1,
  "projectNo": "HR001",
  "changeType": "ROLE_MENU_CHANGED",
  "payload": {
    "roleId": 5
  },
  "operator": {
    "userId": 10000,
    "userName": "管理员张三"
  }
}
```

**业务系统处理逻辑**：
1. 根据 `roleId` 查询该角色下所有有效用户
2. 推送给在线用户，通知前端刷新菜单

---

### 4.3 角色模块权限变更（ROLE_MODULE_PERMISSION_CHANGED）

**触发场景**：
- 修改角色对某个模块的权限（ sys_role_module）

**payload 结构**：

```json
{
  "roleId": 5,
  "moduleId": 20
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `roleId` | Long | 是 | 变更的角色ID |
| `moduleId` | Long | 是 | 变更的模块ID |

**完整消息示例**：
```json
{
  "messageId": "msg_1718692800003",
  "version": "1.0",
  "publishTime": "2026-06-18T10:32:00.003+08:00",
  "subjectId": 1,
  "projectNo": "HR001",
  "changeType": "ROLE_MODULE_PERMISSION_CHANGED",
  "payload": {
    "roleId": 5,
    "moduleId": 20
  },
  "operator": {
    "userId": 10000,
    "userName": "管理员张三"
  }
}
```

**业务系统处理逻辑**：
1. 根据 `roleId` 查询该角色下所有有效用户
2. 检查用户是否在使用该 `moduleId` 对应的功能页面
3. 推送给在线用户，通知前端刷新当前页面或权限

---

### 4.4 角色配置变更（ROLE_CONFIG_CHANGED）

**触发场景**：

- 修改角色基本信息（角色名称、描述等）
- 修改角色状态（启用/禁用）
- 删除角色

**payload 结构**：

```json
{
  "roleId": 5
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `roleId` | Long | 是 | 变更的角色ID |

**完整消息示例**：
```json
{
  "messageId": "msg_1718692800004",
  "publishTime": "2026-06-18T10:33:00.004+08:00",
  "subjectId": 1,
  "projectNo": "HR001",
  "changeType": "ROLE_CONFIG_CHANGED",
  "payload": {
    "roleId": 5
  },
  "operator": {
    "userId": 10000,
    "userName": "管理员张三"
  }
}
```

**业务系统处理逻辑**：
1. 根据 `roleId` 查询该角色下所有有效用户

---

### 4.5 角色交互权限变更（ROLE_INTERACTION_PERMISSION_CHANGED）

**触发场景**：
- 修改角色的交互权限（sys_role_interaction_permission）

**payload 结构**：
```json
{
  "roleId": 5,
  "affectedUserIds": [10001, 10002],
  "affectedSubjectIds": [1, 2]
}
```

---

### 4.6 角色数据权限变更（ROLE_DATA_PERMISSION_CHANGED）

**触发场景**：
- 修改角色的数据权限（sys_role_data_permission）

**payload 结构**：
```json
{
  "roleId": 5,
  "affectedUserIds": [10001, 10002],
  "affectedSubjectIds": [1, 2]
}
```

---

### 4.7 角色特殊权限变更（ROLE_SPECIAL_PERMISSION_CHANGED）

**触发场景**：
- 修改角色的特殊权限（sys_role_special_permission）

**payload 结构**：
```json
{
  "roleId": 5,
  "affectedUserIds": [10001, 10002],
  "affectedSubjectIds": [1, 2]
}
```

---

## 五、changeType 汇总表

| changeType | 说明 | payload 字段 | 业务系统处理要点 |
|------------|------|-------------|-----------------|
| `MODULE_CHANGED` | 模块变更 | `moduleId` | 根据业务需求决定是否推送，可能影响所有用户 |
| `ROLE_MENU_CHANGED` | 角色菜单权限变更 | `roleId`, `affectedUserIds`, `affectedSubjectIds` | 查询该角色的有效用户，推送通知刷新菜单 |
| `ROLE_MODULE_PERMISSION_CHANGED` | 角色模块权限变更 | `roleId`, `moduleId`, `affectedUserIds`, `affectedSubjectIds` | 查询该角色的有效用户，推送通知刷新对应模块页面 |
| `ROLE_CONFIG_CHANGED` | 角色配置变更 | `roleId`, `affectedUserIds`, `affectedSubjectIds` | 查询该角色的有效用户，根据操作类型决定处理方式 |
| `ROLE_INTERACTION_PERMISSION_CHANGED` | 角色交互权限变更 | `roleId`, `affectedUserIds`, `affectedSubjectIds` | 查询该角色的有效用户，推送通知刷新交互权限 |
| `ROLE_DATA_PERMISSION_CHANGED` | 角色数据权限变更 | `roleId`, `affectedUserIds`, `affectedSubjectIds` | 查询该角色的有效用户，推送通知刷新数据权限 |
| `ROLE_SPECIAL_PERMISSION_CHANGED` | 角色特殊权限变更 | `roleId`, `affectedUserIds`, `affectedSubjectIds` | 查询该角色的有效用户，推送通知刷新特殊权限 |

---

## 六、config-biz 实现方案：基于注解的统一消息推送

### 6.1 设计思路

参考项目现有的 `@DataSource` 注解切面实现，设计 `@ConfigChangeNotify` 注解 + AOP 切面，实现声明式消息推送。

**核心优势**：
- **无侵入**：业务方法无需手动调用发布接口，只需声明注解
- **统一管理**：所有消息发布逻辑集中在切面中，便于维护
- **自动提取**：支持 SpEL 表达式从方法参数或返回值中提取 payload 字段

---

### 6.2 注解定义

#### 6.2.1 `@ConfigChangeNotify` 核心注解

**文件位置**：`jdec-platform-shared/src/main/java/com/jdec/platform/shared/annotation/ConfigChangeNotify.java`

```java
package com.jdec.platform.shared.annotation;

import com.jdec.platform.shared.enums.ChangeType;
import java.lang.annotation.*;

/**
 * 配置变更通知注解
 * 标注在 Service 方法上，方法执行成功后自动发布 Redis 消息
 *
 * <p>使用示例：
 * <pre>
 * // 示例1：角色菜单变更（从参数中提取 roleId）
 * {@literal @}ConfigChangeNotify(
 *     changeType = ChangeType.ROLE_MENU_CHANGED,
 *     roleIdExpr = "#request.roleId"
 * )
 * public void saveRoleMenu(SaveRoleMenuReq request) { ... }
 *
 * // 示例2：角色模块权限变更（提取 roleId 和 moduleId）
 * {@literal @}ConfigChangeNotify(
 *     changeType = ChangeType.ROLE_MODULE_PERMISSION_CHANGED,
 *     roleIdExpr = "#request.roleId",
 *     moduleIdExpr = "#request.moduleId"
 * )
 * public void saveRoleModuleFieldPermission(SaveRoleModuleFieldPermissionReq request) { ... }
 *
 * // 示例3：模块变更（从返回值中提取 moduleId）
 * {@literal @}ConfigChangeNotify(
 *     changeType = ChangeType.MODULE_CHANGED,
 *     moduleIdExpr = "#result"
 * )
 * public Long saveModule(String projectNo, Long subjectId, SaveModuleReq request) { ... }
 *
 * // 示例4：角色配置变更（直接传参数）
 * {@literal @}ConfigChangeNotify(
 *     changeType = ChangeType.ROLE_CONFIG_CHANGED,
 *     roleIdExpr = "#roleId"
 * )
 * public void deleteRole(Long roleId) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConfigChangeNotify {

    /**
     * 变更类型
     */
    ChangeType changeType();

    /**
     * 提取 roleId 的 SpEL 表达式
     * <p>支持从参数或返回值中提取：
     * <ul>
     *   <li>#request.roleId - 从参数对象的属性中提取</li>
     *   <li>#roleId - 直接从参数中提取</li>
     *   <li>#result - 从方法返回值中提取（需确保方法已执行）</li>
     * </ul>
     * <p><b>重要</b>：如果表达式求值结果为 null，切面将<b>不会发布消息</b>。
     * 这适用于新增/编辑共用方法的场景（新增时 id 为 null，不发送消息；编辑时 id 存在，发送消息）。
     */
    String roleIdExpr() default "";

    /**
     * 提取 moduleId 的 SpEL 表达式
     * <p><b>重要</b>：如果表达式求值结果为 null，且 roleId 也为 null，切面将<b>不会发布消息</b>。
     */
    String moduleIdExpr() default "";

    /**
     * 是否在方法抛异常时也发送消息（默认 false，只在成功时发送）
     */
    boolean notifyOnException() default false;

    /**
     * 是否要求 payload 至少有一个非 null 字段才发布消息（默认 true）
     * <p>设置为 true 时，如果所有 SpEL 表达式求值结果都是 null，将不发布消息。
     * <p>这对于新增/编辑共用的方法特别有用：新增时 id 为 null，自动跳过消息发布。
     */
    boolean requireNonEmptyPayload() default true;
}
```

#### 6.2.2 `ChangeType` 枚举

**文件位置**：`jdec-platform-shared/src/main/java/com/jdec/platform/shared/enums/ChangeType.java`

```java
package com.jdec.platform.shared.enums;

/**
 * 配置变更类型枚举
 */
public enum ChangeType {
    /** 模块变更 */
    MODULE_CHANGED("MODULE_CHANGED", "模块变更"),

    /** 角色菜单变更 */
    ROLE_MENU_CHANGED("ROLE_MENU_CHANGED", "角色菜单变更"),

    /** 角色模块权限变更 */
    ROLE_MODULE_PERMISSION_CHANGED("ROLE_MODULE_PERMISSION_CHANGED", "角色模块权限变更"),

    /** 角色配置变更 */
    ROLE_CONFIG_CHANGED("ROLE_CONFIG_CHANGED", "角色配置变更"),

    /** 角色交互权限变更 */
    ROLE_INTERACTION_PERMISSION_CHANGED("ROLE_INTERACTION_PERMISSION_CHANGED", "角色交互权限变更"),

    /** 角色数据权限变更 */
    ROLE_DATA_PERMISSION_CHANGED("ROLE_DATA_PERMISSION_CHANGED", "角色数据权限变更"),

    /** 角色特殊权限变更 */
    ROLE_SPECIAL_PERMISSION_CHANGED("ROLE_SPECIAL_PERMISSION_CHANGED", "角色特殊权限变更");

    private final String code;
    private final String description;

    ChangeType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
```

---

### 6.3 AOP 切面实现

**文件位置**：`jdec-platform-shared/src/main/java/com/jdec/platform/shared/aspect/ConfigChangeNotifyAspect.java`

```java
package com.jdec.platform.shared.aspect;

import com.jdec.platform.shared.annotation.ConfigChangeNotify;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.ConfigChangeContext;
import com.jdec.platform.shared.enums.ChangeType;
import com.jdec.platform.shared.service.ConfigChangeMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置变更通知切面
 *
 * <p>拦截标注了 {@link ConfigChangeNotify} 注解的方法，在方法执行成功后自动发布 Redis 消息
 *
 * <p>{@code @Order(100)} 确保在事务提交之后执行（事务切面默认 {@code @Order(Ordered.LOWEST_PRECEDENCE)}）
 */
@Slf4j
@Aspect
@Order(100)
@Component
@RequiredArgsConstructor
public class ConfigChangeNotifyAspect {

    private final ConfigChangeMessagePublisher messagePublisher;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Pointcut("@annotation(com.jdec.platform.shared.annotation.ConfigChangeNotify)")
    public void configChangeNotifyPointcut() {}

    @Around("configChangeNotifyPointcut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        ConfigChangeNotify annotation = method.getAnnotation(ConfigChangeNotify.class);

        Object result = null;
        Throwable exception = null;

        try {
            // 执行目标方法
            result = point.proceed();
            return result;
        } catch (Throwable e) {
            exception = e;
            throw e;
        } finally {
            // 1. 异常时默认不发（除非 notifyOnException=true）
            boolean shouldNotify = (exception == null) || annotation.notifyOnException();

            // 2. 检查当前事务是否已被标记为 rollback-only
            //    场景：业务代码调用 setRollbackOnly() 后不抛异常，但事务最终仍会回滚
            if (shouldNotify && TransactionSynchronizationManager.isActualTransactionActive()) {
                try {
                    // isRollbackOnly() 返回 true 说明事务即将回滚，跳过消息发布
                    if (TransactionAspectSupport
                            .currentTransactionStatus()
                            .isRollbackOnly()) {
                        log.warn(
                                "事务已标记 rollback-only，跳过消息发布: method={}, changeType={}",
                                method.getName(),
                                annotation.changeType());
                        shouldNotify = false;
                    }
                } catch (Exception ignored) {
                    // 获取事务状态失败时保守处理：不影响主流程，继续发消息
                }
            }

            if (shouldNotify) {
                try {
                    publishMessage(annotation, point, result);
                } catch (Exception e) {
                    // 消息发布失败不影响业务，记录日志即可
                    log.error(
                            "发布配置变更消息失败: method={}, changeType={}",
                            method.getName(),
                            annotation.changeType(),
                            e);
                }
            }

            // 无论是否发布消息，都必须清理 ThreadLocal，防止内存泄漏
            ConfigChangeContext.clear();
        }
    }

    /** 发布消息 */
    private void publishMessage(
            ConfigChangeNotify annotation, ProceedingJoinPoint point, Object result) {
        ChangeType changeType = annotation.changeType();
        String roleIdExpr = annotation.roleIdExpr();
        String moduleIdExpr = annotation.moduleIdExpr();

        // 构建 SpEL 上下文
        EvaluationContext context = buildEvaluationContext(point, result);

        // 构建 payload
        Map<String, Object> payload = new HashMap<>();

        // 提取 roleId
        if (StringUtils.hasText(roleIdExpr)) {
            Long roleId = parseExpression(roleIdExpr, context, Long.class);
            if (roleId != null) {
                payload.put("roleId", roleId);
            }
        }

        // 提取 moduleId
        if (StringUtils.hasText(moduleIdExpr)) {
            Long moduleId = parseExpression(moduleIdExpr, context, Long.class);
            if (moduleId != null) {
                payload.put("moduleId", moduleId);
            }
        }

        // 从 ThreadLocal 读取受影响的用户/主体 ID（由 Service 层在操作前/后 set）
        List<Long> affectedUserIds = ConfigChangeContext.getAffectedUserIds();
        List<Long> affectedSubjectIds = ConfigChangeContext.getAffectedSubjectIds();
        if (!affectedUserIds.isEmpty()) {
            payload.put("affectedUserIds", affectedUserIds);
        }
        if (!affectedSubjectIds.isEmpty()) {
            payload.put("affectedSubjectIds", affectedSubjectIds);
        }

        // 【关键】校验 payload 是否为空
        // 如果 requireNonEmptyPayload=true 且 payload 为空，则不发送消息
        if (annotation.requireNonEmptyPayload() && payload.isEmpty()) {
            log.debug(
                    "payload 为空（可能是新增操作），跳过消息发布: changeType={}, method={}",
                    changeType.getCode(),
                    point.getSignature().getName());
            return;
        }

        // 发布消息
        try {
            Long subjectId = AppContext.getSubjectId();
            String projectNo = AppContext.getProjectNo();

            messagePublisher.publish(subjectId, projectNo, changeType.getCode(), payload);

            log.info(
                    "配置变更消息发布成功: changeType={}, subjectId={}, projectNo={}, payload={}",
                    changeType.getCode(),
                    subjectId,
                    projectNo,
                    payload);
        } catch (Exception e) {
            log.error("发布消息到 Redis 失败", e);
        }
    }

    /** 构建 SpEL 求值上下文 */
    private EvaluationContext buildEvaluationContext(ProceedingJoinPoint point, Object result) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 注册方法参数（#argName 形式）
        MethodSignature signature = (MethodSignature) point.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = point.getArgs();

        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }

        // 注册返回值（#result 形式）
        if (result != null) {
            context.setVariable("result", result);
        }

        return context;
    }

    /** 解析 SpEL 表达式 */
    private <T> T parseExpression(String expression, EvaluationContext context, Class<T> type) {
        try {
            return parser.parseExpression(expression).getValue(context, type);
        } catch (Exception e) {
            log.warn("SpEL 表达式解析失败: expression={}", expression, e);
            return null;
        }
    }
}
```

**核心特性**：

1. **事务安全**：通过 `@Order(100)` 确保在事务提交后执行，并检查事务 rollback-only 状态
2. **异常处理**：支持 `notifyOnException` 配置，默认只在成功时发送
3. **SpEL 提取**：从方法参数或返回值中提取 roleId/moduleId
4. **受影响用户追踪**：从 `ConfigChangeContext` 读取 affectedUserIds/affectedSubjectIds
5. **ThreadLocal 清理**：在 finally 中必定清理，防止内存泄漏

---

### 6.4 Redis 消息发布服务

**文件位置**：`jdec-platform-shared/src/main/java/com/jdec/platform/shared/service/ConfigChangeMessagePublisher.java`

```java
package com.jdec.platform.shared.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.shared.dto.ConfigChangeMessage;
import com.jdec.platform.shared.enums.ChangeType;
import com.jdec.platform.shared.utils.ConfigChangeChannelUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * 配置变更消息发布服务
 *
 * <p>负责将配置变更消息发布到 Redis Pub/Sub Channel
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigChangeMessagePublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 发布配置变更消息
     *
     * @param subjectId 主体ID
     * @param projectNo 项目编码
     * @param changeType 变更类型
     * @param payload 变更内容（业务系统根据 changeType 自行解析）
     */
    public void publish(
            Long subjectId, String projectNo, ChangeType changeType, Map<String, Object> payload) {
        publish(subjectId, projectNo, changeType.getCode(), payload);
    }

    /**
     * 发布配置变更消息（字符串类型）
     */
    public void publish(
            Long subjectId, String projectNo, String changeType, Map<String, Object> payload) {
        try {
            // 构建 channel
            String channel = ConfigChangeChannelUtil.buildChannel(subjectId, projectNo);

            // 构建消息体
            ConfigChangeMessage message = buildMessage(subjectId, projectNo, changeType, payload);

            // 序列化并发布
            String messageJson = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, messageJson);

            log.info(
                    "配置变更消息发布成功: channel={}, changeType={}, payload={}",
                    channel,
                    changeType,
                    payload);
            log.debug("完整消息内容: {}", messageJson);
        } catch (Exception e) {
            log.error(
                    "Redis 消息发布失败: subjectId={}, projectNo={}, changeType={}",
                    subjectId,
                    projectNo,
                    changeType,
                    e);
            // 消息发布失败不抛异常，避免影响业务流程
        }
    }

    /** 构建完整消息体 */
    private ConfigChangeMessage buildMessage(
            Long subjectId, String projectNo, String changeType, Map<String, Object> payload) {
        ConfigChangeMessage message = new ConfigChangeMessage();
        message.setMessageId(generateMessageId());
        message.setPublishTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        message.setSubjectId(subjectId);
        message.setProjectNo(projectNo);
        message.setChangeType(changeType);
        message.setPayload(payload);

        // 操作人信息暂时注释（根据项目需要启用）
        // 可以从 SecurityUtils 或 AppContext 获取当前登录用户信息

        return message;
    }

    /**
     * 生成消息ID
     * 格式：msg_{timestamp}_{random}
     */
    private String generateMessageId() {
        return "msg_"
                + System.currentTimeMillis()
                + "_"
                + UUID.randomUUID().toString().substring(0, 8);
    }
}
```

**辅助工具类**：

`jdec-platform-shared/src/main/java/com/jdec/platform/shared/utils/ConfigChangeChannelUtil.java`

```java
/**
 * 配置变更 Redis Channel 工具类
 */
public class ConfigChangeChannelUtil {
    private static final String CHANNEL_PREFIX = "jdec:config:change";

    /**
     * 构建 Redis Channel
     * 格式：jdec:config:change:{subjectId}:{projectNo}
     */
    public static String buildChannel(Long subjectId, String projectNo) {
        return String.format("%s:%d:%s", CHANNEL_PREFIX, subjectId, projectNo);
    }

    /**
     * 构建通配符 Channel（用于订阅）
     * 格式：jdec:config:change:{subjectId}:*
     */
    public static String buildChannelPattern(Long subjectId) {
        return String.format("%s:%d:*", CHANNEL_PREFIX, subjectId);
    }

    /**
     * 构建通配符 Channel（订阅所有主体）
     * 格式：jdec:config:change:*:*
     */
    public static String buildChannelPatternAll() {
        return String.format("%s:*:*", CHANNEL_PREFIX);
    }
}
```

**消息体 DTO**：

`jdec-platform-shared/src/main/java/com/jdec/platform/shared/dto/ConfigChangeMessage.java`

```java
/**
 * 配置变更消息体
 */
@Data
public class ConfigChangeMessage {
    /** 消息唯一ID */
    private String messageId;

    /** 发布时间 */
    private String publishTime;

    /** 主体ID */
    private Long subjectId;

    /** 项目编码 */
    private String projectNo;

    /** 变更类型 */
    private String changeType;

    /** 变更内容（业务系统根据 changeType 自行解析） */
    private Map<String, Object> payload;

    /** 操作人信息 */
    private OperatorInfo operator;

    @Data
    public static class OperatorInfo {
        private Long userId;
        private String userName;
    }
}
```

---

### 6.5 实际使用示例

#### 示例1：角色菜单变更（SysRolePermissionService）

```java
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysRolePermissionService implements SysRolePermissionApi {

    // ... 省略其他代码

    @Override
    @ConfigChangeNotify(
        changeType = ChangeType.ROLE_MENU_CHANGED,
        roleIdExpr = "#request.roleId"
    )
    public void saveRoleMenu(SaveRoleMenuReq request) {
        Long roleId = request.getRoleId();
        List<Long> menuIds = request.getMenuIds();

        // 删除旧的角色菜单关联
        sysRoleMenuMapper.delete(
                Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, roleId));

        // 批量插入新的角色菜单关联
        if (CollUtil.isNotEmpty(menuIds)) {
            List<SysRoleMenu> roleMenus =
                    menuIds.stream()
                            .map(menuId -> {
                                SysRoleMenu roleMenu = new SysRoleMenu();
                                roleMenu.setRoleId(roleId);
                                roleMenu.setMenuId(menuId);
                                return roleMenu;
                            })
                            .toList();
            sysRoleMenuMapper.insert(roleMenus);
        }
        // 方法执行成功后，切面自动发送消息：
        // {
        //   "changeType": "ROLE_MENU_CHANGED",
        //   "payload": { "roleId": 5 }
        // }
    }
}
```

#### 示例2：角色模块字段权限变更

```java
@Transactional(rollbackFor = Exception.class)
@Override
@ConfigChangeNotify(
    changeType = ChangeType.ROLE_MODULE_PERMISSION_CHANGED,
    roleIdExpr = "#request.roleId",
    moduleIdExpr = "#request.fields[0].moduleId"  // 从第一个 field 中提取 moduleId
)
public void saveRoleModuleFieldPermission(SaveRoleModuleFieldPermissionReq request) {
    // ... 业务逻辑
    // 方法执行成功后，切面自动发送消息：
    // {
    //   "changeType": "ROLE_MODULE_PERMISSION_CHANGED",
    //   "payload": { "roleId": 5, "moduleId": 20 }
    // }
}
```

#### 示例3：模块变更（SysModuleService）- 新增/编辑共用方法

```java
@Override
@Transactional
@ConfigChangeNotify(
    changeType = ChangeType.MODULE_CHANGED,
    moduleIdExpr = "#request.id"  // 从请求对象中提取 id
)
public Long saveModule(String projectNo, Long subjectId, SaveModuleReq request) {
    Long moduleId = request.getId();

    if (moduleId == null) {
        // 新增逻辑
        SysModule module = new SysModule();
        // ... 设置属性
        sysModuleMapper.insert(module);
        moduleId = module.getId();
        // 【注意】新增时 request.getId() 为 null，切面不会发送消息（payload为空）
    } else {
        // 编辑逻辑
        SysModule module = sysModuleMapper.selectById(moduleId);
        // ... 更新属性
        sysModuleMapper.updateById(module);
        // 【注意】编辑时 request.getId() 有值，切面会发送消息：
        // {
        //   "changeType": "MODULE_CHANGED",
        //   "payload": { "moduleId": 20 }
        // }
    }

    return moduleId;
}

@Override
@Transactional
@ConfigChangeNotify(
    changeType = ChangeType.MODULE_CHANGED,
    moduleIdExpr = "#moduleId"  // 直接从参数中提取
)
public void deleteModuleComplete(Long moduleId) {
    // ... 删除逻辑
    // moduleId 肯定有值，切面会发送消息
}
```

#### 示例4：角色配置变更（SysRoleService）

```java
@Override
@ConfigChangeNotify(
    changeType = ChangeType.ROLE_CONFIG_CHANGED,
    roleIdExpr = "#roleId"
)
public void deleteRole(Long roleId) {
    // ... 业务逻辑
    // 方法执行成功后，切面自动发送消息：
    // {
    //   "changeType": "ROLE_CONFIG_CHANGED",
    //   "payload": { "roleId": 5 }
    // }
}

@Override
@ConfigChangeNotify(
    changeType = ChangeType.ROLE_CONFIG_CHANGED,
    roleIdExpr = "#result.id"  // 从返回值对象中提取
)
public RoleConfigPageResp saveRoleUser(SaveRoleUserReq request) {
    // ... 业务逻辑
    return getRoleConfigById(roleId);
}
```

---

### 6.6 新增/编辑共用方法的处理策略

**场景说明**：很多业务方法采用"新增/编辑共用"模式，通过请求对象中的 `id` 是否为 null 来判断是新增还是编辑。

#### 策略1：从请求对象中提取 id（推荐）

```java
@ConfigChangeNotify(
    changeType = ChangeType.ROLE_CONFIG_CHANGED,
    roleIdExpr = "#request.id"
)
public void saveRole(SaveRoleReq request) {
    Long roleId = request.getId();
    if (roleId == null) {
        // 新增：roleId 为 null，切面不会发送消息（payload 为空）
        // ... 插入逻辑
    } else {
        // 编辑：roleId 有值，切面会发送消息
        // ... 更新逻辑
    }
}
```

**优点**：
- ✅ 新增时自动跳过（id为null，payload为空，切面不发送）
- ✅ 编辑时自动发送（id有值，payload包含roleId）
- ✅ 代码简洁，无需额外判断

**缺点**：

- ⚠️ 新增场景不会发送消息，如果新增也需要通知，不适用

---

#### 策略2：从返回值中提取 id（新增/编辑都通知）

```java
@ConfigChangeNotify(
    changeType = ChangeType.ROLE_CONFIG_CHANGED,
    roleIdExpr = "#result"
)
public Long saveRole(SaveRoleReq request) {
    Long roleId = request.getId();
    if (roleId == null) {
        // 新增逻辑
        SysRole role = new SysRole();
        sysRoleMapper.insert(role);
        roleId = role.getId();  // MyBatis-Plus 自动回填
    } else {
        // 编辑逻辑
        sysRoleMapper.updateById(...);
    }
    return roleId;  // 新增/编辑都会返回 roleId，切面都会发送消息
}
```

**优点**：
- ✅ 新增和编辑都会发送消息
- ✅ 适用于需要全量通知的场景

**缺点**：
- ⚠️ 新增角色时可能没有用户，通知意义不大（但不影响功能）

---

#### 策略3：条件表达式（精细控制）

```java
@ConfigChangeNotify(
    changeType = ChangeType.ROLE_CONFIG_CHANGED,
    roleIdExpr = "#request.id != null ? #request.id : null"
)
public void saveRole(SaveRoleReq request) {
    // ... 业务逻辑
    // SpEL 表达式返回 null 时，切面不会发送消息
}
```

**优点**：
- ✅ 最灵活，可以在 SpEL 中加任意条件判断

**缺点**：
- ⚠️ 表达式复杂，可读性差

---

#### 推荐使用场景对比

| 场景 | 推荐策略 | 注解配置 | 说明 |
|------|---------|---------|------|
| 角色编辑（不关心新增） | 策略1 | `roleIdExpr = "#request.id"` | 新增时自动跳过 |
| 模块新增/编辑（都要通知） | 策略2 | `moduleIdExpr = "#result"` | 新增/编辑都发送 |
| 复杂条件判断 | 策略3 | `roleIdExpr = "条件表达式"` | 按需定制 |

---

### 6.7 ConfigChangeContext - 受影响用户追踪

**文件位置**：`jdec-platform-shared/src/main/java/com/jdec/platform/shared/context/ConfigChangeContext.java`

```java
/**
 * 配置变更上下文（ThreadLocal）
 *
 * 用于在 Service 方法与 ConfigChangeNotifyAspect 之间传递受影响的用户/主体 ID 列表。
 */
public final class ConfigChangeContext {
    private static final ThreadLocal<Payload> HOLDER = new ThreadLocal<>();

    /**
     * 设置受影响的用户/主体 ID 列表
     */
    public static void set(List<Long> affectedUserIds, List<Long> affectedSubjectIds) {
        HOLDER.set(new Payload(affectedUserIds, affectedSubjectIds));
    }

    /** 获取受影响的用户 ID 列表，未设置时返回空列表 */
    public static List<Long> getAffectedUserIds() {
        Payload p = HOLDER.get();
        return p != null && p.userIds != null ? p.userIds : Collections.emptyList();
    }

    /** 获取受影响的主体 ID 列表，未设置时返回空列表 */
    public static List<Long> getAffectedSubjectIds() {
        Payload p = HOLDER.get();
        return p != null && p.subjectIds != null ? p.subjectIds : Collections.emptyList();
    }

    /** 清理 ThreadLocal，防止内存泄漏。Aspect 在 finally 中负责调用 */
    public static void clear() {
        HOLDER.remove();
    }

    private record Payload(List<Long> userIds, List<Long> subjectIds) {}
}
```

**使用示例**：

在 `SysRolePermissionService` 中，所有权限变更方法都会调用 `setAffectedContext()` 方法：

```java
/**
 * 查询角色关联的用户/主体 ID，写入 ConfigChangeContext，供 Aspect 追加到 payload
 *
 * 所有权限变更方法（菜单、交互权限、数据权限、特殊权限、模块字段权限）在操作完成后统一调用此方法
 */
private void setAffectedContext(Long roleId) {
    // 查询该角色下的所有用户
    List<Long> affectedUserIds =
            sysRoleUserMapper
                    .selectList(new LambdaQueryWrapper<SysRoleUser>()
                            .eq(SysRoleUser::getRoleId, roleId))
                    .stream()
                    .map(SysRoleUser::getUserId)
                    .distinct()
                    .toList();

    // 查询该角色关联的所有主体
    List<Long> affectedSubjectIds =
            sysRoleSubjectMapper
                    .selectList(new LambdaQueryWrapper<SysRoleSubject>()
                            .eq(SysRoleSubject::getRoleId, roleId))
                    .stream()
                    .map(SysRoleSubject::getSubjectId)
                    .distinct()
                    .toList();

    // 写入 ThreadLocal
    ConfigChangeContext.set(affectedUserIds, affectedSubjectIds);
}

// 在每个权限变更方法的最后调用
@Override
@ConfigChangeNotify(changeType = ChangeType.ROLE_MENU_CHANGED, roleIdExpr = "#request.roleId")
public void saveRoleMenu(SaveRoleMenuReq request) {
    // ... 业务逻辑

    setAffectedContext(request.getRoleId());  // 设置受影响的用户/主体
}
```

Aspect 会自动从 `ConfigChangeContext` 读取并追加到 payload：

```java
// 从 ThreadLocal 读取受影响的用户/主体 ID（由 Service 层在操作前/后 set）
List<Long> affectedUserIds = ConfigChangeContext.getAffectedUserIds();
List<Long> affectedSubjectIds = ConfigChangeContext.getAffectedSubjectIds();
if (!affectedUserIds.isEmpty()) {
    payload.put("affectedUserIds", affectedUserIds);
}
if (!affectedSubjectIds.isEmpty()) {
    payload.put("affectedSubjectIds", affectedSubjectIds);
}
```

---

### 6.8 触发时机汇总表

| 操作场景 | 所在Service | 方法名 | changeType | 注解配置 | 是否设置 affectedContext |
|---------|------------|--------|-----------|---------|------------------------|
| 新增模块 | SysModuleService | saveModule | MODULE_CHANGED | `moduleIdExpr = "#result"` | ❌ |
| 修改模块 | SysModuleService | saveModule | MODULE_CHANGED | `moduleIdExpr = "#result"` | ❌ |
| 删除模块 | SysModuleService | deleteModuleComplete | MODULE_CHANGED | `moduleIdExpr = "#moduleId"` | ❌ |
| 修改角色菜单 | SysRolePermissionService | saveRoleMenu | ROLE_MENU_CHANGED | `roleIdExpr = "#request.roleId"` | ✅ |
| 修改角色模块权限 | SysRolePermissionService | saveRoleModuleFieldPermission | ROLE_MODULE_PERMISSION_CHANGED | `roleIdExpr = "#request.roleId"`, `moduleIdExpr = "#request.fields[0].moduleId"` | ✅ |
| 修改角色交互权限 | SysRolePermissionService | saveRoleInteractionPermission | ROLE_INTERACTION_PERMISSION_CHANGED | `roleIdExpr = "#request.roleId"` | ✅ |
| 修改角色数据权限 | SysRolePermissionService | saveRoleDataPermission | ROLE_DATA_PERMISSION_CHANGED | `roleIdExpr = "#request.roleId"` | ✅ |
| 修改角色特殊权限 | SysRolePermissionService | saveRoleSpecialPermission | ROLE_SPECIAL_PERMISSION_CHANGED | `roleIdExpr = "#request.roleId"` | ✅ |
| 新增角色 | SysRoleService | saveRoleUser | ROLE_CONFIG_CHANGED | `roleIdExpr = "#result.id"` | ❓ (待实现) |
| 修改角色 | SysRoleService | saveRoleUser | ROLE_CONFIG_CHANGED | `roleIdExpr = "#result.id"` | ❓ (待实现) |
| 删除角色 | SysRoleService | deleteRole | ROLE_CONFIG_CHANGED | `roleIdExpr = "#roleId"` | ❓ (待实现) |

---

### 6.9 核心优势对比

| 维度 | 手动调用方式 | 注解+AOP方式（推荐） |
|------|------------|------------------|
| **代码侵入性** | 高，每个方法都要手动调用 | 低，只需添加注解 |
| **维护成本** | 高，修改消息格式需改多处 | 低，集中在切面中修改 |
| **遗漏风险** | 高，容易忘记调用 | 低，编译期即可检查 |
| **灵活性** | 低，难以统一处理异常情况 | 高，切面统一处理 |
| **可读性** | 差，业务代码混杂消息发布逻辑 | 好，声明式编程，意图清晰 |

---

##

---

## 七、消息流转完整示意

以"修改角色菜单权限"为例：

```
管理员操作 config-biz
    │  调用 SysRoleMenuService.updateRoleMenus(roleId=5)
    │
    ├─ 1. 更新数据库：sys_role_menu 表
    │
    └─ 2. 发布 Redis 消息到 channel: jdec:config:change:1:HR001
       {
         "changeType": "ROLE_MENU_CHANGED",
         "payload": { "roleId": 5 }
       }

hr-biz 订阅 channel: jdec:config:change:1:HR001
    │
    ├─ 1. 解析 changeType = ROLE_MENU_CHANGED
    │
    ├─ 2. 查询 sys_role_user: roleId=5 的有效用户 → [10001, 10002, 10003]
    │
    ├─ 3. 过滤当前在线用户 → [10001, 10002]
    │
    ├─ 4. 构建 WebSocket 消息（actionRequired = REFRESH_MENU）
    │
    └─ 5. 推送给 10001、10002 的 WebSocket 连接
       10003 离线 → 不做处理（或存入消息记录表）

前端收到 WebSocket 消息
    │
    ├─ actionRequired = REFRESH_MENU
    │
    └─ 调用菜单接口刷新，重新渲染导航菜单
```

---

## 八、扩展性

未来如果需要推送其他配置变更（如审批链、系统参数等），可以：
1. 新增 `changeType` 枚举
2. 定义对应的 `payload` 结构
3. 业务系统在消息处理器中新增对应分支

保持 channel 命名规则和消息基础结构不变，扩展性良好。

---

---

## 常见问题

**Q：新增和编辑用同一个方法，新增时也想发通知怎么办？**
用策略2，从返回值中提取 id（`roleIdExpr = "#result"`），方法返回新增后的 id 即可。

**Q：SpEL 表达式写错了会怎样？**
切面会捕获 SpEL 解析异常并记录 warn 日志，视为 null，不发送消息，不影响业务。

**Q：消息发布失败了会影响业务吗？**
不影响。消息发布失败只记录 error 日志，业务结果已经提交。

**Q：`@Transactional` 和 `@ConfigChangeNotify` 一起用，消息会在事务提交前发吗？**
不会。切面使用 `@Order(100)`，事务切面默认是 `Ordered.LOWEST_PRECEDENCE`（Integer.MAX_VALUE），
所以 ConfigChangeNotifyAspect 在外层，事务在内层，消息在事务提交后才发布。

**Q：同一个方法能标多个通知吗？**
目前不支持，一个方法对应一种 changeType。如果一个操作会触发多种变更，建议拆分方法或在 Service 中手动调用 publisher。

---

**文档版本**：v2.1
**创建日期**：2026-06-18
**更新日期**：2026-06-18
