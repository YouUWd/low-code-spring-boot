# 审计日志新增操作支持 - 变更记录

## 变更概述

为审计日志系统添加了对**新增操作（CREATE）**的支持。新增操作会生成 diff 列表，其中 `oldValue` 为空，`newValue` 为前端传入的每个字段值。

## 变更内容

### 1. 枚举类型扩展

**文件**: `jdec-platform-shared/src/main/java/com/jdec/platform/shared/audit/enums/OperationType.java`

**变更**: 添加 `CREATE` 操作类型

```java
public enum OperationType {
    CREATE("新增"),   // 新增
    UPDATE("修改"),
    DELETE("删除");
}
```

### 2. JSON差异对比工具扩展

**文件**: `jdec-platform-config-biz/src/main/java/com/jdec/platform/config/biz/audit/util/JsonDiffUtil.java`

**变更**: 添加 `buildCreateDiffs` 方法

```java
/**
 * 构建新增操作的字段差异列表（所有字段的oldValue为null，newValue为传入值）
 */
public <T> List<FieldDiff> buildCreateDiffs(String json, Class<T> clazz)
```

**功能说明**:
- 遍历新增数据的所有字段
- 每个字段的 `oldValue` 设置为 `null`
- 每个字段的 `newValue` 为实际传入的值
- 支持关联字段（RELATION）、枚举字段（ENUM）、字典字段（DICT）的自动解析

### 3. 注解式审计切面扩展

**文件**: `jdec-platform-config-biz/src/main/java/com/jdec/platform/config/biz/audit/aspect/DataAuditAspect.java`

**变更**: 添加 `handleCreateAudit` 方法

```java
/** 处理CREATE操作审计 */
private void handleCreateAudit(
        ProceedingJoinPoint joinPoint,
        DataAudit dataAudit,
        Long dataId,
        String tableName)
```

**功能说明**:
- 在方法执行后获取新增的数据
- 调用 `JsonDiffUtil.buildCreateDiffs` 生成 diff 列表
- 发送审计日志到HTTP接口
- 创建数据快照（如果有 dataId）

**使用示例**:
```java
@DataAudit(
    module = "角色管理",
    operation = OperationType.CREATE,  // 使用 CREATE
    tableName = "sys_role",
    dataIdField = "#result.id"  // 从返回值获取ID
)
@Transactional
public RoleCreateResult createRole(RoleCreateReq request) {
    // 插入数据
    roleMapper.insert(role);
    return RoleCreateResult.builder().id(role.getId()).build();
}
```

### 4. 编程式审计服务扩展

**文件**: `jdec-platform-config-biz/src/main/java/com/jdec/platform/config/biz/audit/service/DataAuditService.java`

**变更**: 添加 `auditCreate` 方法

```java
/**
 * 新增数据审计
 */
public void auditCreate(
        String module,
        String subModule,
        String tableName,
        Long dataId,
        Object newData)
```

**功能说明**:
- 将新增数据转换为 JSON
- 调用 `JsonDiffUtil.buildCreateDiffs` 生成 diff 列表
- 发送审计日志
- 创建初始快照

**使用示例**:
```java
@Transactional
public void createRole(RoleCreateReq req) {
    // 1. 插入数据
    roleMapper.insert(role);

    // 2. 记录审计日志
    dataAuditService.auditCreate(
        "系统设置",
        "角色管理",
        "sys_role",
        role.getId(),
        req
    );
}
```

### 5. 审计日志工具类扩展

**文件**: `jdec-platform-config-biz/src/main/java/com/jdec/platform/config/biz/util/AuditLogHelper.java`

**变更**: 添加 `logCreate` 方法

```java
/**
 * 记录新增操作（带差异列表）
 */
public void logCreate(
        String subModule,
        String tableName,
        String operation,
        Long dataId,
        Object newData)
```

**使用示例**:
```java
@Transactional
public void createRole(RoleCreateReq req) {
    roleMapper.insert(role);

    // 记录审计日志（带diff）
    auditLogHelper.logCreate(
        "角色管理",
        "sys_role",
        "新增",
        role.getId(),
        req
    );
}
```

### 6. 使用示例代码

**文件**: `jdec-platform-config-biz/src/main/java/com/jdec/platform/config/biz/audit/example/CreateAuditExample.java`

包含以下使用场景：
1. 注解式：单条新增（从返回值获取ID）
2. 编程式：单条新增（手动调用）
3. 编程式：批量新增（逐条记录）
4. 编程式：批量新增（汇总记录）

### 7. 使用文档更新

**文件**: `jdec-platform-config-biz/src/main/java/com/jdec/platform/config/biz/audit/AUDIT_USAGE.md`

**变更内容**:
- 功能特性：添加"新增操作支持"
- 枚举类型：添加 `CREATE` 操作类型说明
- 使用示例：添加"示例0：新增操作审计"
- 编程式API：添加"新增审计"方法说明

## 审计日志格式

### CREATE 操作的 diff 格式

```json
{
  "remark": "[
    {
      \"fieldName\": \"角色名称\",
      \"fieldCode\": \"roleName\",
      \"oldValue\": null,
      \"newValue\": \"管理员\",
      \"fieldType\": \"SIMPLE\"
    },
    {
      \"fieldName\": \"所属部门\",
      \"fieldCode\": \"deptId\",
      \"oldValue\": null,
      \"newValue\": \"研发部\",
      \"fieldType\": \"RELATION\"
    }
  ]"
}
```

**特点**:
- 所有字段的 `oldValue` 都为 `null`
- `newValue` 为实际新增的值
- 关联字段会自动解析为中文名称
- 枚举字段会自动解析为中文描述
- 字典字段会自动解析为中文 label

## 使用场景对比

| 操作类型 | oldValue | newValue | 用途 |
|---------|----------|----------|------|
| CREATE  | null     | 新增的值 | 记录数据新增 |
| UPDATE  | 旧值     | 新值     | 记录数据修改 |
| DELETE  | 删除的值 | null     | 记录数据删除 |

## 注意事项

1. **dataIdField 配置**:
   - CREATE 操作通常从返回值获取ID：`dataIdField = "#result.id"`
   - 也可以在方法执行前就有ID（如UUID）：`dataIdField = "#request.id"`

2. **快照创建**:
   - CREATE 操作会自动创建初始快照
   - 如果没有 dataId，则不会创建快照（但仍会记录审计日志）

3. **批量新增**:
   - 可以逐条记录（每条都有 diff）
   - 可以汇总记录（只记录操作，不带 diff）

4. **事务要求**:
   - 审计方法必须在事务内执行
   - 审计日志在事务提交后才会发送

## 向后兼容性

- 所有现有的 UPDATE 和 DELETE 操作不受影响
- 现有代码无需修改即可继续使用
- 新增功能为可选项，不影响现有功能

## 测试建议

1. 测试单条新增审计（注解式）
2. 测试单条新增审计（编程式）
3. 测试批量新增审计（逐条记录）
4. 测试批量新增审计（汇总记录）
5. 测试关联字段、枚举字段、字典字段的解析
6. 测试事务回滚时审计日志不发送

## 相关文件清单

- `jdec-platform-shared/src/main/java/com/jdec/platform/shared/audit/enums/OperationType.java`
- `jdec-platform-config-biz/src/main/java/com/jdec/platform/config/biz/audit/util/JsonDiffUtil.java`
- `jdec-platform-config-biz/src/main/java/com/jdec/platform/config/biz/audit/aspect/DataAuditAspect.java`
- `jdec-platform-config-biz/src/main/java/com/jdec/platform/config/biz/audit/service/DataAuditService.java`
- `jdec-platform-config-biz/src/main/java/com/jdec/platform/config/biz/util/AuditLogHelper.java`
- `jdec-platform-config-biz/src/main/java/com/jdec/platform/config/biz/audit/example/CreateAuditExample.java`
- `jdec-platform-config-biz/src/main/java/com/jdec/platform/config/biz/audit/AUDIT_USAGE.md`
