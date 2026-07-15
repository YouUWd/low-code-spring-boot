# 数据快照和审计功能使用文档

## 概述

数据快照和审计功能提供了自动记录数据变更的能力，通过AOP切面自动捕获数据修改和删除操作，对比变更前后的差异，并生成审计日志。

## 功能特性

1. **自动快照管理**：自动保存数据快照，支持版本号乐观锁
2. **字段级差异对比**：精确识别哪些字段发生了变化
3. **关联字段解析**：自动查询关联表，显示中文名称而非ID
4. **事务同步**：审计日志与业务操作在同一事务中，保证一致性
5. **灵活配置**：通过注解灵活配置审计行为

## 核心组件

### 1. 注解

#### @DataAudit（方法级注解）
```java
@DataAudit(
    module = "系统设置",           // 业务模块名称
    subModule = "审批链配置",      // 业务子模块名称
    operation = OperationType.UPDATE,  // 操作类型：UPDATE/DELETE
    tableName = "sys_approval_chain_config",  // 数据库表名
    dataIdField = "#request.id"    // 数据ID的SpEL表达式
)
```

#### @AuditField（字段级注解）
```java
@AuditField(
    name = "审批角色",              // 字段中文名
    type = FieldType.RELATION,     // 字段类型：SIMPLE/RELATION
    target = "sys_role",           // 关联表名（RELATION类型必填）
    idField = "id",                // 关联表ID字段名
    nameField = "role_name"        // 关联表名称字段名
)
```

### 2. 枚举类型

#### OperationType（操作类型）
- `UPDATE`：修改操作
- `DELETE`：删除操作

#### FieldType（字段类型）
- `SIMPLE`：简单类型（字符串、数字等）
- `RELATION`：关联类型（外键ID，需要查询显示名称）
- `ENUM`：枚举类型（枚举值转中文描述）
- `DICT`：字典类型（字典值转中文label，基于sys_config_category配置）

## 快速开始

### 步骤1：创建数据库表

执行 `sys_data_snapshot.sql` 脚本创建快照表：

```sql
CREATE TABLE sys_data_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_name VARCHAR(100) NOT NULL,
    data_id BIGINT NOT NULL,
    json_data TEXT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_table_data (table_name, data_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据快照表';
```

### 步骤2：创建请求DTO并添加字段注解

```java
@Data
public class ApprovalChainConfigUpdateReq {

    @AuditField(name = "主键ID", ignore = true)
    private Long id;

    @AuditField(name = "模块ID")
    private Long moduleId;

    @AuditField(
        name = "审批角色",
        type = FieldType.RELATION,
        target = "sys_role",
        idField = "id",
        nameField = "role_name"
    )
    private Long approveRoleId;

    @AuditField(name = "当前步骤")
    private Integer currentStep;

    @AuditField(name = "是否跳过", type = FieldType.SIMPLE)
    private Integer skipped;
}
```

### 步骤3：在Service方法上添加审计注解

```java
@Service
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysApprovalChainConfigService {

    /**
     * 更新审批链配置
     */
    @DataAudit(
        module = "系统设置",
        subModule = "审批链配置",
        operation = OperationType.UPDATE,
        tableName = "sys_approval_chain_config",
        dataIdField = "#request.id"
    )
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(ApprovalChainConfigUpdateReq request) {
        // 业务逻辑
        SysApprovalChainConfig config = BeanUtil.toBean(request, SysApprovalChainConfig.class);
        sysApprovalChainConfigMapper.updateById(config);
    }

    /**
     * 删除审批链配置
     */
    @DataAudit(
        module = "系统设置",
        subModule = "审批链配置",
        operation = OperationType.DELETE,
        tableName = "sys_approval_chain_config",
        dataIdField = "#id"
    )
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(Long id) {
        // 业务逻辑
        sysApprovalChainConfigMapper.deleteById(id);
    }
}
```

### 步骤4：初始化快照数据（可选）

对于已有数据，可以手动初始化快照：

```java
@Autowired
private SysDataSnapshotService snapshotService;

public void initSnapshot(Long dataId) {
    // 查询当前数据
    SysApprovalChainConfig config = mapper.selectById(dataId);

    // 转换为JSON
    String jsonData = JsonUtil.toJson(config);

    // 保存快照
    snapshotService.saveSnapshot("sys_approval_chain_config", dataId, jsonData);
}
```

## 使用示例

### 示例1：简单字段修改审计

```java
@Data
public class UserUpdateReq {
    @AuditField(name = "用户ID", ignore = true)
    private Long id;

    @AuditField(name = "用户名")
    private String userName;

    @AuditField(name = "手机号")
    private String phone;

    @AuditField(name = "状态")
    private Integer status;
}

@Service
public class UserService {

    @DataAudit(
        module = "用户管理",
        operation = OperationType.UPDATE,
        tableName = "sys_user",
        dataIdField = "#request.id"
    )
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(UserUpdateReq request) {
        // 更新用户信息
        sysUserMapper.updateById(BeanUtil.toBean(request, SysUser.class));
    }
}
```

### 示例2：包含关联字段的审计

```java
@Data
public class RoleUpdateReq {
    @AuditField(name = "角色ID", ignore = true)
    private Long id;

    @AuditField(name = "角色名称")
    private String roleName;

    @AuditField(
        name = "所属部门",
        type = FieldType.RELATION,
        target = "department",
        idField = "id",
        nameField = "dept_name"
    )
    private Long deptId;

    @AuditField(
        name = "创建人",
        type = FieldType.RELATION,
        target = "sys_user",
        idField = "user_id",
        nameField = "user_name"
    )
    private Long createdBy;
}
```

### 示例3：删除操作审计

```java
@Service
public class RoleService {

    @DataAudit(
        module = "角色管理",
        operation = OperationType.DELETE,
        tableName = "sys_role",
        dataIdField = "#id"  // 方法参数直接引用
    )
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        sysRoleMapper.deleteById(id);
    }
}
```

### 示例4：枚举类型字段审计

枚举类型用于将Java枚举值转换为中文描述。

**步骤1：枚举类实现 `EnumDescribable` 接口**

```java
import com.jdec.platform.shared.audit.enums.EnumDescribable;

public enum StatusEnum implements EnumDescribable {
    ACTIVE("启用"),
    INACTIVE("停用"),
    PENDING("待审核");

    private final String description;

    StatusEnum(String description) {
        this.description = description;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
```

**步骤2：在DTO中使用ENUM类型**

```java
@Data
public class UserUpdateReq {
    @AuditField(name = "用户ID", ignore = true)
    private Long id;

    @AuditField(name = "用户名")
    private String userName;

    @AuditField(
        name = "状态",
        type = FieldType.ENUM,
        enumClass = StatusEnum.class
    )
    private String status;  // 存储枚举的name或ordinal
}
```

**审计日志效果：**
- 旧值：`ACTIVE` → 显示为：`启用`
- 新值：`INACTIVE` → 显示为：`停用`

### 示例5：字典类型字段审计

字典类型用于将配置在 `sys_config_category` + `sys_config_item` 中的字典值转换为中文label。

**步骤1：在通用配置中添加字典**

在系统中创建配置分类和配置项：
- 配置分类：`sys_config_category.category_alias = "employee_status"`
- 配置项：
  - `value = "1"`, `label = "在职"`
  - `value = "2"`, `label = "离职"`
  - `value = "3"`, `label = "休假"`

**步骤2：在DTO中使用DICT类型**

```java
@Data
public class EmployeeUpdateReq {
    @AuditField(name = "员工ID", ignore = true)
    private Long id;

    @AuditField(name = "员工姓名")
    private String name;

    @AuditField(
        name = "员工状态",
        type = FieldType.DICT,
        dictCategoryAlias = "employee_status"
    )
    private String employeeStatus;  // 存储字典值 "1", "2", "3"

    @AuditField(
        name = "工作类型",
        type = FieldType.DICT,
        dictCategoryAlias = "work_type"
    )
    private String workType;
}
```

**审计日志效果：**
- 旧值：`1` → 显示为：`在职`
- 新值：`2` → 显示为：`离职`

**支持多值（逗号分隔）：**
```java
// 字段值为 "1,2,3"
// 审计日志显示为：在职,离职,休假
```

### 示例6：多层级对象的dataIdField配置

```java
// 场景1：从Request对象获取ID
@DataAudit(
    dataIdField = "#request.id"
)
public void update(UpdateReq request) { }

// 场景2：从Request的嵌套对象获取ID
@DataAudit(
    dataIdField = "#request.data.id"
)
public void update(ComplexReq request) { }

// 场景3：方法直接接收ID参数
@DataAudit(
    dataIdField = "#id"
)
public void delete(Long id) { }

// 场景4：多个参数时指定参数名
@DataAudit(
    dataIdField = "#configId"
)
public void update(Long configId, String name) { }
```

## 审计结果格式

### AuditResult（审计结果）

```json
{
  "module": "系统设置",
  "subModule": "审批链配置",
  "operation": "修改",
  "tableName": "sys_approval_chain_config",
  "dataId": 123,
  "operateTime": "2026-07-15T10:30:00",
  "diffs": [
    {
      "fieldName": "审批角色",
      "fieldCode": "approveRoleId",
      "oldValue": "管理员",
      "newValue": "审批员",
      "fieldType": "RELATION"
    },
    {
      "fieldName": "当前步骤",
      "fieldCode": "currentStep",
      "oldValue": 1,
      "newValue": 2,
      "fieldType": "SIMPLE"
    }
  ]
}
```

## 工作流程

### UPDATE操作流程

1. 方法执行前，切面拦截
2. 从快照表查询旧数据（oldSnapshot）
3. 方法执行，更新数据库
4. 获取方法参数（新数据）
5. 使用JsonDiffUtil对比新旧数据
6. 生成FieldDiff列表
7. 构建AuditResult
8. 事务提交后，发送HTTP审计日志（TODO）
9. 更新快照表（带version版本号校验）

### DELETE操作流程

1. 方法执行前，切面拦截
2. 从快照表查询旧数据（oldSnapshot）
3. 方法执行，删除数据库记录
4. 构建AuditResult（所有字段标记为删除）
5. 事务提交后，发送HTTP审计日志（TODO）
6. 删除快照记录

## 注意事项

### 1. 事务要求

- 被审计的方法必须有 `@Transactional` 注解
- 审计日志在事务提交后才会发送HTTP请求
- 如果事务回滚，审计日志不会发送

### 2. 快照初始化

- 首次使用前需要初始化快照数据
- 可以在数据创建时自动保存快照
- 已有数据需要手动执行初始化脚本

### 3. SpEL表达式

- `dataIdField` 支持SpEL表达式
- `#request.id`：从request对象的id字段获取
- `#id`：从方法参数id获取
- 支持多层级访问：`#request.data.id`

### 4. 字段注解

- `ignore = true`：跳过该字段的审计
- `type = FieldType.SIMPLE`：简单类型（默认）
- `type = FieldType.RELATION`：关联字段，需要配置target、idField、nameField
- `type = FieldType.ENUM`：枚举类型，需要配置enumClass（枚举类必须实现EnumDescribable接口）
- `type = FieldType.DICT`：字典类型，需要配置dictCategoryAlias（对应sys_config_category.category_alias）
- 建议将ID字段标记为 `ignore = true`

### 5. 版本号冲突

- 使用乐观锁防止快照并发更新
- 如果版本号冲突，会抛出异常并回滚事务
- 正常情况下不会出现版本号冲突

### 6. HTTP审计日志接口（待实现）

当前HTTP调用部分使用TODO标注，需要实现：

```java
// TODO: 实现HTTP审计日志接口
// POST /api/audit/log
// Body: AuditResult JSON
```

## 高级配置

### 自定义关联字段查询

如果关联表的查询逻辑复杂，可以扩展 `RelationFieldQueryService`：

```java
@Service
public class CustomRelationQueryService extends RelationFieldQueryService {

    @Override
    public Map<Long, String> queryNameByIds(
            String tableName,
            String idField,
            String nameField,
            List<Long> ids) {

        if ("custom_table".equals(tableName)) {
            // 自定义查询逻辑
            return customQuery(ids);
        }

        // 调用父类默认实现
        return super.queryNameByIds(tableName, idField, nameField, ids);
    }
}
```

### 禁用审计

可以通过 `enabled = false` 临时禁用审计：

```java
@DataAudit(
    module = "测试",
    operation = OperationType.UPDATE,
    tableName = "test_table",
    dataIdField = "#id",
    enabled = false  // 禁用审计
)
```

## 数据库表结构

### sys_data_snapshot（快照表）

| 字段 | 类型 | 说明 |
|-----|------|-----|
| id | BIGINT | 主键ID |
| table_name | VARCHAR(100) | 表名 |
| data_id | BIGINT | 数据ID |
| json_data | TEXT | JSON格式的数据快照 |
| version | INT | 版本号（乐观锁） |
| update_time | DATETIME | 更新时间 |

唯一索引：`uk_table_data (table_name, data_id)`

## 故障排查

### 问题1：快照未更新

**原因**：方法未添加 `@Transactional` 注解
**解决**：确保被审计的方法有事务注解

### 问题2：关联字段显示ID而非名称

**原因**：`@AuditField` 的 `type` 未设置为 `RELATION`
**解决**：正确配置关联字段注解

### 问题3：版本号冲突异常

**原因**：并发更新快照
**解决**：正常情况不应出现，检查是否有多个地方同时更新同一数据

### 问题4：dataIdField解析失败

**原因**：SpEL表达式错误
**解决**：检查表达式是否与方法参数结构匹配

### 问题5：审计日志未生成

**原因**：字段未添加 `@AuditField` 注解
**解决**：确保Request DTO的所有字段都有注解（除了 `ignore = true` 的字段）

## 性能优化建议

1. **批量关联查询**：`RelationFieldQueryService` 使用批量查询，避免N+1问题
2. **快照压缩**：对于大JSON可以考虑压缩存储
3. **异步审计**：可以改为异步发送HTTP审计日志（需修改切面）
4. **定期清理**：定期清理过期快照数据

## 后续扩展

1. 实现HTTP审计日志接口
2. 添加审计日志查询界面
3. 支持审计日志导出
4. 支持数据回滚功能（基于快照）
5. 添加审计日志统计报表
