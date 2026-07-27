# SaveOrUpdate 场景审计日志使用指南

## 问题场景

在实际开发中，经常会遇到**新增和修改共用同一个接口**的场景，例如：
- `saveOrUpdate(request)` - 通过判断 ID 是否为空来决定是新增还是修改
- 前端提交表单时，编辑和新建使用同一个表单组件和接口

这种场景下，审计日志需要：
- **新增时**：记录所有字段的新值（oldValue 为 null）
- **修改时**：记录字段的新旧值差异

## 解决方案

### 方案1：使用 UPDATE 操作类型（推荐）

使用 `OperationType.UPDATE`，系统会**自动判断**是新增还是修改：

```java
@DataAudit(
    module = "系统设置",
    subModule = "配置管理",
    operation = OperationType.UPDATE,  // 使用 UPDATE 类型
    tableName = "sys_config",
    dataIdField = "#request.id"  // 可能为 null（新增）或有值（修改）
)
@Transactional
public void saveOrUpdate(ConfigSaveReq request) {
    if (request.getId() == null) {
        // 新增：系统自动识别为新增，生成 CREATE 类型的 diff
        configMapper.insert(config);
    } else {
        // 修改：系统自动识别为修改，生成 UPDATE 类型的 diff
        configMapper.updateById(config);
    }
}
```

**工作原理**：
1. 方法执行前，系统查询快照表是否存在该记录
2. 如果**快照不存在** → 识别为**新增操作**
   - 生成所有字段的 diff，oldValue 为 null
   - 操作类型显示为 "新增"
3. 如果**快照存在** → 识别为**修改操作**
   - 对比快照和新数据，生成字段差异
   - 操作类型显示为 "修改"

### 方案2：编程式手动判断

对于复杂场景，可以使用编程式 API 手动判断：

```java
@Transactional
public void saveOrUpdate(ConfigSaveReq request) {
    boolean isCreate = (request.getId() == null);

    if (isCreate) {
        // 新增
        configMapper.insert(config);

        // 记录新增审计
        dataAuditService.auditCreate(
            "系统设置",
            "配置管理",
            "sys_config",
            config.getId(),
            request
        );
    } else {
        // 修改
        configMapper.updateById(config);

        // 记录修改审计
        dataAuditService.auditWithCompare(
            "系统设置",
            "配置管理",
            "修改",
            "sys_config",
            request.getId(),
            request
        );
    }
}
```

### 方案3：根据返回值获取 ID

如果 ID 是在方法执行后生成的（如 MyBatis 自动填充），可以从返回值获取：

```java
@DataAudit(
    module = "系统设置",
    subModule = "配置管理",
    operation = OperationType.UPDATE,
    tableName = "sys_config",
    dataIdField = "#result.id"  // 从返回值获取 ID
)
@Transactional
public ConfigSaveResult saveOrUpdate(ConfigSaveReq request) {
    if (request.getId() == null) {
        configMapper.insert(config);  // MyBatis 自动填充 ID
    } else {
        configMapper.updateById(config);
    }

    return ConfigSaveResult.builder()
        .id(config.getId())
        .build();
}
```

## 审计日志效果对比

### 新增场景

**请求**：
```json
{
  "id": null,
  "name": "系统配置",
  "value": "enabled"
}
```

**审计日志 diff**：
```json
[
  {
    "fieldName": "配置名称",
    "fieldCode": "name",
    "oldValue": null,
    "newValue": "系统配置",
    "fieldType": "SIMPLE"
  },
  {
    "fieldName": "配置值",
    "fieldCode": "value",
    "oldValue": null,
    "newValue": "enabled",
    "fieldType": "SIMPLE"
  }
]
```

**日志内容**：`operation = "新增"`

---

### 修改场景

**请求**：
```json
{
  "id": 123,
  "name": "系统配置",
  "value": "disabled"
}
```

**审计日志 diff**（假设原 value 为 "enabled"）：
```json
[
  {
    "fieldName": "配置值",
    "fieldCode": "value",
    "oldValue": "enabled",
    "newValue": "disabled",
    "fieldType": "SIMPLE"
  }
]
```

**日志内容**：`operation = "修改"`

## 完整使用示例

详细代码示例请参考：
- `SaveOrUpdateAuditExample.java` - 包含 4 种使用场景

## 技术实现细节

### 自动识别逻辑（DataAuditAspect）

```java
// 在 handleUpdateAudit 方法中
if (oldSnapshot == null) {
    // 快照不存在，识别为新增操作
    diffs = jsonDiffUtil.buildCreateDiffs(newJson, newData.getClass());
    operation = "新增";  // 覆盖操作类型
} else {
    // 快照存在，识别为修改操作
    diffs = jsonDiffUtil.compareJsonObjects(oldJson, newJson, newData.getClass());
    operation = "修改";
}
```

### buildCreateDiffs 实现（JsonDiffUtil）

```java
public <T> List<FieldDiff> buildCreateDiffs(String json, Class<T> clazz) {
    // 遍历所有字段
    // oldValue 设置为 null
    // newValue 设置为实际值
    return diffs;
}
```

## 注意事项

### 1. 快照初始化

对于已有数据（存量数据），需要先初始化快照：

```java
// 一次性脚本
public void initSnapshots() {
    List<Config> configs = configMapper.selectList(null);
    for (Config config : configs) {
        String json = objectMapper.writeValueAsString(config);
        snapshotService.saveSnapshot("sys_config", config.getId(), json);
    }
}
```

### 2. 事务要求

必须加 `@Transactional` 注解，否则快照更新和审计日志可能不一致。

### 3. dataIdField 配置

- 如果 ID 在**请求参数**中：`dataIdField = "#request.id"`
- 如果 ID 在**返回值**中：`dataIdField = "#result.id"`
- 如果 ID 是**方法参数**：`dataIdField = "#id"`

### 4. 字段注解要求

Request DTO 中的字段必须添加 `@AuditField` 注解，否则不会记录到审计日志中。

## 对比其他方案

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **使用 UPDATE 自动判断** | 简单，一个注解搞定 | - | 绝大多数场景（推荐） |
| **编程式手动判断** | 灵活，可自定义逻辑 | 代码冗长 | 复杂业务逻辑 |
| **从返回值获取 ID** | 支持自动生成 ID | 需要定义返回值类 | MyBatis 自动填充 ID |

## 常见问题

### Q1：为什么推荐使用 UPDATE 而不是 CREATE？

**A**：因为 UPDATE 类型会自动识别是新增还是修改，而 CREATE 类型只能用于纯新增场景。使用 UPDATE 可以让一个注解覆盖两种场景。

### Q2：如果不想自动判断，想明确区分怎么办？

**A**：使用编程式 API（方案2），手动调用 `auditCreate` 或 `auditWithCompare`。

### Q3：新增时会创建快照吗？

**A**：会！在事务提交后，系统会自动创建快照，方便下次修改时对比差异。

### Q4：如果数据库中已有数据，但快照表为空会怎样？

**A**：首次修改时会被识别为"新增"，生成所有字段的 diff。建议对存量数据执行一次快照初始化脚本。

## 总结

对于 **saveOrUpdate** 场景，推荐使用 `OperationType.UPDATE` 配合自动判断机制：

```java
@DataAudit(
    module = "xxx",
    subModule = "xxx",
    operation = OperationType.UPDATE,
    tableName = "xxx",
    dataIdField = "#request.id"
)
```

系统会自动识别是新增还是修改，生成正确的审计日志，无需手动编写判断逻辑。
