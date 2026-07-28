# 数据快照和审计功能使用文档

## 概述

数据快照和审计功能提供了自动记录数据变更的能力，通过AOP切面自动捕获数据修改和删除操作，对比变更前后的差异，并生成审计日志。

## 功能特性

1. **自动快照管理**：自动保存数据快照，支持版本号乐观锁
2. **字段级差异对比**：精确识别哪些字段发生了变化
3. **关联字段解析**：自动查询关联表，显示中文名称而非ID
4. **事务同步**：审计日志与业务操作在同一事务中，保证一致性
5. **灵活配置**：通过注解灵活配置审计行为
6. **无ID场景支持**：支持不传ID的更新操作，仅记录操作不对比差异
7. **新增操作审计**：支持新增操作的审计日志，diff列表中oldValue为空，newValue为新增的字段值
8. **编程式API**：提供 `DataAuditService`，支持复杂业务场景的手动调用
9. **统一HTTP格式**：按照运营日志系统格式封装审计日志

## 核心组件

### 1. 注解

#### @DataAudit（方法级注解）
```java
@DataAudit(
    module = "系统设置",           // 业务模块名称
    subModule = "审批链配置",      // 业务子模块名称（可选）
    operation = OperationType.UPDATE,  // 操作类型：CREATE/UPDATE/DELETE
    tableName = "sys_approval_chain_config",  // 数据库表名
    dataIdField = "#request.id",   // 数据ID的SpEL表达式（可选，留空表示无ID场景）
    forceCompare = false,          // 是否强制对比（仅当dataIdField不为空时生效）
    enabled = true                 // 是否启用（默认true）
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
- `CREATE`：新增操作
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

### 示例1：新增操作审计（注解式）

对于新增操作，审计日志会生成 diff 列表，其中 oldValue 为空，newValue 为前端传入的值。

```java
@Data
public class RoleCreateReq {
    @AuditField(name = "角色名称")
    private String roleName;

    @AuditField(name = "角色编码")
    private String roleCode;

    @AuditField(
        name = "所属部门",
        type = FieldType.RELATION,
        target = "department",
        idField = "id",
        nameField = "dept_name"
    )
    private Long deptId;

    @AuditField(name = "状态")
    private Integer status;
}

@Service
public class RoleService {

    @DataAudit(
        module = "角色管理",
        subModule = "角色配置",
        operation = OperationType.CREATE,
        tableName = "sys_role"
        // 新增操作不需要 dataIdField，因为没有旧数据需要对比
    )
    @Transactional(rollbackFor = Exception.class)
    public void createRole(RoleCreateReq request) {
        // 创建角色
        SysRole role = BeanUtil.toBean(request, SysRole.class);
        roleMapper.insert(role);
    }
}
```

**生成的审计日志：**
```json
{
  "logRemark": "[
    {\"fieldName\":\"角色名称\",\"fieldCode\":\"roleName\",\"oldValue\":null,\"newValue\":\"管理员\",\"fieldType\":\"SIMPLE\"},
    {\"fieldName\":\"角色编码\",\"fieldCode\":\"roleCode\",\"oldValue\":null,\"newValue\":\"admin\",\"fieldType\":\"SIMPLE\"},
    {\"fieldName\":\"所属部门\",\"fieldCode\":\"deptId\",\"oldValue\":null,\"newValue\":\"技术部\",\"fieldType\":\"RELATION\"},
    {\"fieldName\":\"状态\",\"fieldCode\":\"status\",\"oldValue\":null,\"newValue\":1,\"fieldType\":\"SIMPLE\"}
  ]"
}
```

### 示例2：新增操作审计（编程式）

```java
@Service
@RequiredArgsConstructor
public class RoleService {

    private final DataAuditService dataAuditService;
    private final RoleMapper roleMapper;

    @Transactional(rollbackFor = Exception.class)
    public void createRole(RoleCreateReq request) {
        // 创建角色
        SysRole role = BeanUtil.toBean(request, SysRole.class);
        roleMapper.insert(role);

        // 手动记录新增审计（带字段diff）
        dataAuditService.auditCreate(
            "角色管理",        // module
            "角色配置",        // subModule
            "新增",           // operation
            "sys_role",       // tableName
            request           // newData（会生成所有字段的diff，oldValue为null）
        );
    }
}
```

### 示例3：使用 AuditLogHelper 记录新增操作

```java
@Service
@RequiredArgsConstructor
public class RoleService {

    private final AuditLogHelper auditLogHelper;
    private final RoleMapper roleMapper;

    @Transactional(rollbackFor = Exception.class)
    public void createRole(RoleCreateReq request) {
        // 创建角色
        SysRole role = BeanUtil.toBean(request, SysRole.class);
        roleMapper.insert(role);

        // 使用便捷方法记录新增审计
        auditLogHelper.logCreate(
            "角色管理",        // subModule
            "sys_role",       // tableName
            "新增",           // operation
            request           // newData
        );
    }
}
```

### 示例4：简单字段修改审计

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

### 示例7：无ID场景（仅记录操作，不对比差异）

适用于批量操作、无法获取ID或不需要对比差异的场景。

```java
@Service
public class SysStatusService {

    /**
     * 批量更新状态
     * dataIdField留空，只记录操作，不对比差异
     */
    @DataAudit(
        module = "系统设置",
        subModule = "状态配置",
        operation = OperationType.UPDATE,
        tableName = "sys_status",
        dataIdField = ""  // 留空表示无ID场景
    )
    @Transactional
    public void batchUpdateStatus(List<Long> ids, Integer status) {
        ids.forEach(id -> statusMapper.updateStatus(id, status));
    }
}
```

### 示例8：保存或更新（自动识别新增/修改）

**推荐方案：使用 UPDATE 操作，系统自动识别**

```java
@Service
public class ConfigService {

    /**
     * 保存或更新配置
     *
     * 系统会自动识别：
     * - 如果没有旧快照 → 自动生成新增操作的diff（oldValue为null）
     * - 如果有旧快照 → 自动生成修改操作的diff（对比新旧值）
     */
    @DataAudit(
        module = "系统设置",
        subModule = "配置管理",
        operation = OperationType.UPDATE,  // 使用UPDATE即可
        tableName = "sys_config",
        dataIdField = "#req.id"  // 新增时ID可能为null，没问题
    )
    @Transactional
    public void saveOrUpdate(ConfigSaveReq req) {
        if (req.getId() == null) {
            // 新增
            configMapper.insert(req);
        } else {
            // 更新
            configMapper.updateById(req);
        }
    }
}
```

**审计日志效果：**

新增时（快照不存在）：
```json
{
  "operation": "修改",
  "diffs": [
    {"fieldName": "配置名称", "oldValue": null, "newValue": "系统配置"},
    {"fieldName": "配置值", "oldValue": null, "newValue": "enabled"}
  ]
}
```

修改时（快照存在）：
```json
{
  "operation": "修改",
  "diffs": [
    {"fieldName": "配置值", "oldValue": "enabled", "newValue": "disabled"}
  ]
}
```

**详细示例：** 参考 `SaveOrUpdateAuditExample.java`

## 编程式API使用

对于复杂业务逻辑，无法通过注解表达的场景，可以使用 `DataAuditService` 进行手动审计。

### API方法列表

#### 1. 带差异对比的审计

```java
@Autowired
private DataAuditService dataAuditService;

@Transactional
public void updateUser(UserUpdateReq req) {
    // 执行业务逻辑
    userMapper.updateById(user);

    // 手动记录审计（带差异对比）
    dataAuditService.auditWithCompare(
        "用户管理",        // module
        "用户信息",        // subModule
        "修改",           // operation
        "sys_user",       // tableName
        req.getUserId(),  // dataId
        req               // newData（会与快照对比）
    );
}
```

#### 2. 不对比差异的审计

适合批量操作、统计类操作。

```java
@Transactional
public void batchUpdateStatus(List<Long> ids, Integer status) {
    // 批量更新
    ids.forEach(id -> statusMapper.updateStatus(id, status));

    // 只记录操作，不对比差异
    dataAuditService.auditWithoutCompare(
        "系统设置",
        "状态配置",
        "批量修改",
        "sys_status"
    );
}
```

#### 3. 使用JSON字符串审计

适合存储JSON的字段或复杂对象。

```java
@Transactional
public void updateComplexData(Long id, String jsonData) {
    // 存储JSON到数据库
    dataMapper.updateJson(id, jsonData);

    // 使用JSON字符串审计
    dataAuditService.auditWithJson(
        "配置中心",
        "动态配置",
        "修改",
        "sys_dynamic_config",
        id,
        jsonData  // 直接传JSON字符串
    );
}
```

#### 4. 删除审计

```java
@Transactional
public void deleteConfig(Long id) {
    // 删除数据
    configMapper.deleteById(id);

    // 记录删除审计
    dataAuditService.auditDelete(
        "系统设置",
        "配置管理",
        "sys_config",
        id
    );
}
```

### 编程式API示例

#### 示例1：复杂业务逻辑

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final DataAuditService dataAuditService;

    @Transactional
    public void updateOrderWithItems(OrderUpdateReq req) {
        // 1. 更新订单主表
        Order order = new Order();
        BeanUtils.copyProperties(req, order);
        orderMapper.updateById(order);

        // 2. 更新订单明细（批量）
        req.getItems().forEach(item -> {
            orderItemMapper.updateById(item);
        });

        // 3. 记录审计（只记录主表，明细不单独审计）
        dataAuditService.auditWithCompare(
            "订单管理",
            "订单信息",
            "修改",
            "order_main",
            req.getOrderId(),
            req
        );
    }
}
```

#### 示例2：动态表名审计

```java
@Service
@RequiredArgsConstructor
public class DynamicTableService {

    private final DataAuditService dataAuditService;

    @Transactional
    public void updateDynamicTable(String tableName, Long dataId, Map<String, Object> data) {
        // 执行动态SQL更新
        jdbcTemplate.update("UPDATE " + tableName + " SET ... WHERE id = ?", dataId);

        // 动态审计
        String jsonData = JSONUtil.toJsonStr(data);
        dataAuditService.auditWithJson(
            "系统管理",
            tableName,
            "修改",
            tableName,
            dataId,
            jsonData
        );
    }
}
```

## 审计结果格式

### AuditResult（内部审计结果）

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

### HTTP请求格式（发送到运营日志系统）

审计日志会封装为以下格式发送到HTTP接口：

```json
{
  "logType": "operation-log",
  "subjectId": 3,
  "loginUserId": 166,
  "loginRoleId": 29,
  "mockFlag": 0,
  "mockUserId": 0,
  "mockRoleId": 0,
  "ip": "",
  "browser": "",
  "platForm": "",
  "logContent": "{\"module\":\"系统设置\",\"orderId\":\"状态配置\",\"hrefUrl\":\"\",\"operatorResult\":\"修改了配置\"}",
  "logRemark": "[{\"fieldName\":\"状态名称\",\"fieldCode\":\"statusName\",\"oldValue\":\"启用\",\"newValue\":\"停用\",\"fieldType\":\"SIMPLE\"}]"
}
```

#### 字段说明

| 字段 | 说明 | 来源 |
|-----|------|------|
| logType | 固定为 "operation-log" | 系统固定值 |
| subjectId | 当前主体ID | `AppContext.getSubjectId()` |
| loginUserId | 登录用户ID | `AppContext.getUserId()` |
| loginRoleId | 登录角色ID | TODO（待从安全上下文获取） |
| mockFlag | 模拟标识（0：正常，1：模拟） | 固定为0 |
| mockUserId | 模拟用户ID | 固定为0 |
| mockRoleId | 模拟角色ID | 固定为0 |
| ip | IP地址 | TODO（待从请求上下文获取） |
| browser | 浏览器 | TODO（待从请求上下文获取） |
| platForm | 操作平台 | TODO（待从请求上下文获取） |
| logContent | 日志内容（JSON字符串） | 见下文 |
| logRemark | 日志备注（字段差异JSON字符串） | `diffs` 转JSON |

#### logContent 结构

```json
{
  "module": "系统设置",
  "orderId": "状态配置",
  "hrefUrl": "",
  "operatorResult": "修改了配置"
}
```

- `module`：业务模块（`@DataAudit.module`）
- `orderId`：业务单号/标识（优先使用 `subModule`，否则使用 `tableName`）
- `hrefUrl`：跳转链接（暂时为空，后续填充）
- `operatorResult`：操作结果（格式：`{operation}了{subModule或"配置"}`）

#### logRemark 结构

`logRemark` 是字段差异列表 `List<FieldDiff>` 转为JSON字符串：

```json
[
  {
    "fieldName": "状态名称",
    "fieldCode": "statusName",
    "oldValue": "启用",
    "newValue": "停用",
    "fieldType": "SIMPLE"
  },
  {
    "fieldName": "排序",
    "fieldCode": "sortOrder",
    "oldValue": 1,
    "newValue": 2,
    "fieldType": "SIMPLE"
  }
]
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

### 3. SpEL表达式与无ID场景

- `dataIdField` 支持SpEL表达式，也可以留空（无ID场景）
- `#request.id`：从request对象的id字段获取
- `#id`：从方法参数id获取
- 支持多层级访问：`#request.data.id`
- `dataIdField = ""`：无ID场景，只记录操作不对比差异

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

### 6. HTTP审计日志接口

#### 当前状态

当前HTTP发送部分标记为TODO，审计日志会记录到日志文件中。

#### 待实现内容

需要配置并实现以下部分：

1. **配置审计服务地址**（`application.yml`）：
```yaml
audit:
  service:
    url: http://audit-service/api/audit/log
```

2. **实现HTTP调用**（`DataAuditAspect` 和 `DataAuditService`）：
```java
RestTemplate restTemplate = new RestTemplate();
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
HttpEntity<AuditLogRequest> httpRequest = new HttpEntity<>(request, headers);
ResponseEntity<Void> response = restTemplate.postForEntity(auditServiceUrl, httpRequest, Void.class);
```

3. **补充用户上下文信息**：
- `loginRoleId`：从 `SecurityUtils` 或 `LoginUser` 获取当前角色ID
- `ip`、`browser`、`platForm`：从HTTP请求中解析（使用 `ServletRequestAttributes`）

### 7. 编程式API使用场景

使用 `DataAuditService` 适合以下场景：
- 业务逻辑过于复杂，无法通过注解表达
- 需要动态传入参数（dataId、tableName、jsonData等）
- 一个方法中需要审计多个不同的数据变更
- 批量操作只记录一条审计日志

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
3. **异步审计**：可以改为异步发送HTTP审计日志（使用消息队列）
4. **定期清理**：定期清理过期快照数据
5. **批量操作优化**：使用 `auditWithoutCompare()` 避免逐条对比

## 后续扩展

1. **完成HTTP接口对接**：实现审计日志服务的HTTP调用
2. **丰富用户上下文**：补充角色ID、IP、浏览器等信息
3. **支持hrefUrl动态生成**：根据模块和dataId生成详情页链接
4. **添加审计日志查询界面**：提供查询、导出、统计等功能
5. **支持数据回滚功能**：基于快照实现数据回滚
6. **异步发送优化**：使用消息队列异步发送，避免阻塞业务
