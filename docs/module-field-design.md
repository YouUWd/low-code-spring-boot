# 模块与字段功能设计文档

> 适用模块：`jdec-platform-config-api` / `jdec-platform-config-biz`
> 数据源：`config_center`
> 最后更新：2026-05-13

---

## 目录

1. [整体概念](#1-整体概念)
2. [数据模型与表结构](#2-数据模型与表结构)
3. [模块管理接口](#3-模块管理接口)
4. [字段配置接口](#4-字段配置接口)
5. [角色字段权限](#5-角色字段权限)
6. [核心业务逻辑详解](#6-核心业务逻辑详解)
7. [代码导航速查](#7-代码导航速查)

---

## 1. 整体概念

### 1.1 模块是什么？

**模块（SysModule）** 是系统的逻辑功能单元，代表一个业务数据视图（如"员工列表"、"员工详情"）。模块以**树形结构**组织，支持多级嵌套，前端通过模块树来构建侧边栏导航。

每个模块有两种类型：

| 类型 | 说明 |
|------|------|
| `LIST` | 列表模块，展示多条数据记录，需要配置表头（`tableHeader`） |
| `DETAIL` | 详情模块，展示单条记录全字段，需要配置关联状态（`moduleStatuses`） |

### 1.2 模块与字段的关系

```
SysModule（模块）
├── SysModuleTable（模块关联的物理表，可多张）
├── SysModuleSimpleField（简单字段：单表单列映射）
├── SysModuleCombineField（组合字段：多表多列聚合成一个逻辑字段）
└── SysModuleStatus（仅DETAIL类型：关联状态流配置）

SysField（全局字段元配置：表名+列名 -> 显示名/关联关系/加密）
└── SysFieldValue（字段业务值：字段在特定模块下的业务枚举值/审批链配置）
```

**区别一句话总结：**
- `SysModuleSimpleField` / `SysModuleCombineField` → 描述**某个模块用哪些字段**
- `SysField` / `SysFieldValue` → 描述**某个物理字段本身的配置**（显示名、加密、关联关系）

---

## 2. 数据模型与表结构

### 2.1 sys_module（模块主表）

```
id              BIGINT AUTO_INCREMENT   主键
project_no      VARCHAR                 项目编号（租户隔离键）
subject_id      BIGINT                  主体ID
module_code     VARCHAR                 模块唯一编码（如 MOD-EMP-LIST）
module_name     VARCHAR                 模块名称
module_desc     VARCHAR                 模块描述
parent_id       BIGINT                  父模块ID（0表示根节点）
module_level    INT                     层级深度（根=1，往下递增）
module_path     VARCHAR                 路径字符串（如 EMP/EMP-LIST）
primary_table   VARCHAR                 主表名称
module_type     VARCHAR                 LIST | DETAIL
approval_required INT                   是否启用审批 0/1
module_status   VARCHAR                 active | inactive | archived
leaf_flag       INT                     是否叶子节点 0/1（自动维护）
sort_order      INT                     同级排序（从0开始，自动维护）
table_header    TEXT (JSON)             列表表头配置（仅LIST类型用）
```

**module_path 格式说明：**
路径由各级 `module_code` 用 `/` 连接，例如：
```
根节点 EMP                   → module_path = "EMP"
子节点 EMP-LIST              → module_path = "EMP/EMP-LIST"
孙节点 EMP-LIST-BASIC        → module_path = "EMP/EMP-LIST/EMP-LIST-BASIC"
```
跨父移动时，系统会**级联更新**所有子孙节点的 `module_path` 和 `module_level`。

**table_header JSON 格式（仅 LIST 类型）：**
```json
[
  {
    "name": "工号",
    "field": "emp_no",
    "width": 120,
    "sortOrder": 1,
    "searchType": "text",
    "fixed": "left",
    "ellipsis": true,
    "sortable": true
  }
]
```

---

### 2.2 sys_module_table（模块关联表）

记录一个模块涉及的所有物理表，以及表间关联关系（用于 JOIN 查询）。

```
id              BIGINT AUTO_INCREMENT
module_id       BIGINT                  关联 sys_module.id
table_name      VARCHAR                 物理表名
table_desc      VARCHAR                 描述
join_left_field VARCHAR                 左表关联字段（主表字段）
join_right_field VARCHAR                右表关联字段（本表字段）
relation_type   VARCHAR                 关系类型：1:1 | 1:N | N:1
sort_order      INT                     排序
```

**示例：** 员工详情模块关联 `user_base` 表
```
module_id       = 10
table_name      = user_base
join_left_field = id          ← sys_user.id
join_right_field = user_id    ← user_base.user_id
relation_type   = 1:1
```

---

### 2.3 sys_module_simple_field（简单字段）

单表单列映射，描述模块使用某表的哪个列，以及可选的转换表达式。

```
id              BIGINT AUTO_INCREMENT
module_id       BIGINT
table_name      VARCHAR                 所在物理表
column_name     VARCHAR                 列名
transformer     VARCHAR                 转换表达式（可选，如 UPPER(${emp_no})）
transformer_env VARCHAR                 转换环境：database | frontend | none
```

---

### 2.4 sys_module_combine_field（组合字段）

多表多列聚合成一个逻辑字段，用于在前端呈现一个虚拟字段（如"全名"=姓+名）。

```
id              BIGINT AUTO_INCREMENT
module_id       BIGINT
logical_field   VARCHAR                 逻辑字段名（驼峰，如 fullName）
display_name    VARCHAR                 前端显示名（如 全名）
source_mapping  TEXT (JSON)             物理字段来源映射
transformer     VARCHAR                 转换表达式
transformer_env VARCHAR                 转换环境
enabled         INT                     0-禁用 1-启用
```

**source_mapping JSON 格式：**
```json
[
  {"entity": "user", "field": "last_name", "sort_order": 1},
  {"entity": "user", "field": "first_name", "sort_order": 2}
]
```

---

### 2.5 sys_module_status（模块状态，仅DETAIL类型）

将系统全局状态与模块关联，表示该详情模块支持哪些状态流转。

```
id          BIGINT AUTO_INCREMENT
module_id   BIGINT
status_pid  BIGINT    状态父ID（引用 sys_status 表）
status_id   BIGINT    状态ID（引用 sys_status 表）
```

---

### 2.6 sys_field（全局字段元配置）

独立于模块，按"表名+列名"记录一个字段的全局显示配置。

```
id                   BIGINT AUTO_INCREMENT
project_no           VARCHAR    租户隔离
table_name           VARCHAR    物理表名
column_name          VARCHAR    列名
display_name         VARCHAR    前端显示名
relation_business_no VARCHAR    关联业务编号
relation_name        VARCHAR    关联名称
encrypted            INT        是否加密存储 0/1
deleted              INT        软删除
```

---

### 2.7 sys_field_value（字段业务值）

字段在某个模块下的业务枚举/配置值，例如"状态"字段在不同模块下有不同的枚举选项，或绑定不同的审批链。

```
id                   BIGINT AUTO_INCREMENT
field_id             BIGINT    关联 sys_field.id
module_id            BIGINT    关联模块
module_name          VARCHAR   模块名称（冗余）
field_value          VARCHAR   配置值（如枚举值、业务编号）
approval_chain_type_id BIGINT  关联审批链类型
sort_order           INT       排序
enabled              INT       0-禁用 1-启用
deleted              INT       软删除
```

---

### 2.8 sys_role_module_field_permission（角色字段权限）

控制某角色对某模块的某个字段的增删改查权限。

```
id          BIGINT AUTO_INCREMENT
role_id     BIGINT    角色ID
module_id   BIGINT    模块ID
field_type  VARCHAR   simple | combine
field_id    BIGINT    对应 simple/combine 字段表的 ID
readable    INT       可读 0/1
writable    INT       可写（新增）0/1
updatable   INT       可更新 0/1
```

---

## 3. 模块管理接口

**Controller：** `SysModuleController`
**路由前缀：** `/api/config/modules`
**租户隔离：** 通过请求头 `X-Project-No` 指定，由 `AppContext.getProjectNo()` 获取

### 3.1 获取模块列表

```
GET /api/config/modules
```

返回当前项目下**所有模块的扁平列表**，按 `parent_id` 和 `sort_order` 升序排列。前端自行构建树形结构。

**响应字段（SysModuleListResp）：** 基本信息 + `moduleType`、`leafFlag`、`sortOrder`、`modulePath`

---

### 3.2 获取模块完整信息

```
GET /api/config/modules/{moduleCode}/complete
```

按模块编码查询完整配置，一次性返回：
- 模块基本信息（含 `tableHeader` 解析后的对象数组）
- 关联表列表（`moduleTables`）
- 简单字段列表（`simpleFields`）
- 组合字段列表（`combineFields`）
- 状态列表（`moduleStatuses`，**仅 DETAIL 类型才会返回**）

---

### 3.3 保存/编辑模块（创建或更新）

```
POST /api/config/modules/complete
Body: SaveModuleReq
```

通过 `module.id` 是否为空判断创建还是更新：
- **id 为空** → 创建新模块（自动计算层级、路径、排序、更新父节点 leafFlag）
- **id 不为空** → 更新现有模块（只更新名称、描述、主表、类型、审批标志、tableHeader）

子配置（关联表、字段、状态）均采用**全量替换**策略：先删后插。

**SaveModuleReq 结构：**
```json
{
  "module": {
    "id": null,               // 创建时不传
    "moduleCode": "MOD-EMP-LIST",
    "moduleName": "员工列表",
    "parentId": 0,            // 0 = 根节点
    "primaryTable": "sys_user",
    "moduleType": "LIST",
    "approvalRequired": 0,
    "tableHeader": [ ... ]    // 仅 LIST 类型
  },
  "moduleTables": [
    {
      "tableName": "user_base",
      "joinLeftField": "id",
      "joinRightField": "user_id",
      "relationType": "1:1",
      "sortOrder": 1
    }
  ],
  "simpleFields": [
    { "tableName": "sys_user", "columnName": "emp_no", "transformer": null }
  ],
  "combineFields": [
    {
      "logicalField": "fullName",
      "displayName": "全名",
      "sourceMapping": [
        {"entity": "sys_user", "field": "last_name", "sort_order": 1},
        {"entity": "sys_user", "field": "first_name", "sort_order": 2}
      ],
      "transformer": "CONCAT(${last_name}, ${first_name})",
      "enabled": 1
    }
  ],
  "moduleStatuses": [         // 仅 DETAIL 类型
    { "statusPid": 1, "statusId": 10 }
  ]
}
```

---

### 3.4 删除模块

```
DELETE /api/config/modules/{moduleId}/complete
```

物理删除模块及其所有子配置（关联表、简单字段、组合字段、状态）。
**前提：** 模块不能有子模块，否则报错 400。
删除后自动更新父模块的 `leaf_flag`。

---

### 3.5 移动模块（拖拽排序）

```
POST /api/config/modules/move
Body: { "moduleId": 5, "targetParentId": 2, "targetSortOrder": 1 }
```

支持两种移动场景：

| 场景 | 处理逻辑 |
|------|----------|
| **同父内排序** | 全量重排同父所有子节点的 `sort_order`（从 0 连续编号） |
| **跨父移动** | 1. 旧父组全量重排；2. 更新被移动节点的 `parentId`/`moduleLevel`/`modulePath`；3. 新父组插入并全量重排；4. 级联更新所有子孙节点的路径和层级 |

**防循环校验：** 不允许将模块移动到其自身子孙节点下。
**根节点：** `targetParentId = 0` 表示移动到根级。

---

## 4. 字段配置接口

**Controller：** `SysFieldController`
**路由前缀：** `/api/config`

### 4.1 获取所有表信息

```
GET /api/config/tables
```

查询当前项目数据源 `INFORMATION_SCHEMA.TABLES`，返回所有表的表名和注释。
`projectNo` 直接作为 MySQL 的 `TABLE_SCHEMA` 参数使用。

---

### 4.2 获取表的列信息（含配置合并）

```
GET /api/config/tables/{tableName}/columns
```

返回某张表所有列的元数据，并**自动合并** `sys_field` 中已有的显示名配置：
- 若该列已有 `sys_field` 配置 → 使用配置的 `displayName`
- 若无配置 → 优先用数据库注释，再用列名

**响应（ColumnInfoResp）：**
```json
{
  "columnName": "emp_no",
  "columnType": "varchar(50)",
  "columnComment": "工号",
  "displayName": "员工编号"   // 来自 sys_field 配置或注释
}
```

---

### 4.3 查询字段配置列表（元数据驱动）

```
GET /api/config/tables/{tableName}/columns/settings
```

以**数据库元数据为主驱动**，合并 `sys_field` 配置后返回完整列表：
- 每列都会出现，无论是否已配置
- 已配置的列附带 `fieldValues`（字段业务值列表）

**响应（SysFieldResp）：**
```json
{
  "id": 1,
  "projectNo": "P001",
  "tableName": "sys_user",
  "columnName": "status",
  "displayName": "状态",
  "relationBusinessNo": "BIZ-STATUS-001",
  "relationName": "员工状态",
  "encrypted": 0,
  "fieldValues": [
    {
      "id": 10,
      "fieldId": 1,
      "moduleId": 5,
      "moduleName": "员工详情",
      "fieldValue": "ACTIVE",
      "approvalChainTypeId": null,
      "sortOrder": 0,
      "enabled": 1
    }
  ]
}
```

---

### 4.4 保存字段配置

```
POST /api/config/tables/{tableName}/columns/settings
Body: SysFieldSaveReq
```

id 为空时新建，否则更新。**级联处理 `fieldValues`**：按 `fieldId` 全量删除旧值后重新插入。

**请求体：**
```json
{
  "id": null,
  "columnName": "status",
  "displayName": "状态",
  "relationBusinessNo": "BIZ-STATUS-001",
  "relationName": "员工状态",
  "encrypted": 0,
  "fieldValues": [
    {
      "moduleId": 5,
      "moduleName": "员工详情",
      "fieldValue": "ACTIVE",
      "approvalChainTypeId": null,
      "sortOrder": 0,
      "enabled": 1
    }
  ]
}
```

---

## 5. 角色字段权限

**实体：** `SysRoleModuleFieldPermission`（表 `sys_role_module_field_permission`）

记录某角色对某模块内某个字段的权限：

```json
{
  "roleId": 1,
  "moduleId": 10,
  "fieldType": "simple",   // 或 "combine"
  "fieldId": 5,            // 对应 sys_module_simple_field.id 或 sys_module_combine_field.id
  "readable": 1,
  "writable": 1,
  "updatable": 0
}
```

**跨模块接口：** `ModuleFieldQueryService`（定义在 shared 模块）提供根据 `moduleCode` 获取字段编码列表的能力，供其他业务模块（如 HR）在做权限过滤时调用。

---

## 6. 核心业务逻辑详解

### 6.1 模块保存的全量替换策略

子配置（`moduleTables`、`simpleFields`、`combineFields`、`moduleStatuses`）保存时，**不做增量 Diff，直接先删后批量插入**。

**影响：** 每次 POST 保存整个模块完整信息时，子配置表中该 moduleId 下的旧数据会被清空。前端应在请求体中携带所有子配置的完整列表，不能只传变化项。

```java
// saveModuleTables 示例
sysModuleTableMapper.delete(wrapper);  // 先全删
Db.saveBatch(tableList);               // 再批量插入
```

---

### 6.2 模块树的路径与层级维护

**创建模块时：**
1. `module_level = parent.module_level + 1`（根节点为 1）
2. `module_path = parent.module_path + "/" + module_code`（根节点直接为 module_code）
3. 父节点 `leaf_flag` 若为 1（叶子），自动改为 0

**删除模块时：**
1. 检查有无子节点，有则拒绝
2. 物理删除 + 所有子配置
3. 更新父节点 `leaf_flag`

**移动模块时（跨父）：**
递归遍历所有子孙节点，将路径中旧前缀替换为新前缀，层级加上 `levelDiff`：

```java
childNewPath = newRootPath + childOldPath.substring(oldRootPath.length());
child.setModuleLevel(child.getModuleLevel() + levelDiff);
```

---

### 6.3 排序全量洗牌算法（reshuffleAndUpdate）

移动模块后，同父所有子节点的 `sort_order` 重新从 0 连续编号：

```
移动前: [A(0), B(1), C(2), D(3)]
把 D 拖到 B 前面
移动后: [A(0), D(1), B(2), C(3)]
```

只有 `sort_order` 实际变化的记录才会生成 UPDATE SQL（Diff 优化）。
使用 MyBatis-Plus 的部分字段更新（partial object），避免全字段覆盖。

---

### 6.4 字段元数据驱动查询（querySysFields）

`GET /tables/{tableName}/columns/settings` 的核心逻辑：

```
1. 从 INFORMATION_SCHEMA 拿该表所有列
2. 从 sys_field 拿已有配置
3. 从 sys_field_value 拿扩展值（按 fieldId 分组）
4. 遍历数据库列，若有配置则合并，若无则返回默认值（displayName 取注释）
```

**好处：** 新增数据库列后无需手动维护配置表，接口自动识别并展示。

---

### 6.5 数据源切换

所有 Module 和 Field 相关 Service 均标注 `@DataSource(DataSourceConstants.CONFIG_CENTER)`，AOP 自动将数据库连接切换到 `config_center` 数据源：

```java
@Service
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysModuleService implements SysModuleApi { ... }
```

---

## 7. 代码导航速查

| 文件 | 路径 | 说明 |
|------|------|------|
| `SysModuleController` | `config-biz/.../controller/SysModuleController.java` | 模块 HTTP 接口入口 |
| `SysFieldController` | `config-biz/.../controller/SysFieldController.java` | 字段配置 + 元数据 HTTP 接口 |
| `SysModuleService` | `config-biz/.../service/SysModuleService.java` | 模块所有业务逻辑 |
| `SysFieldService` | `config-biz/.../service/SysFieldService.java` | 字段配置所有业务逻辑 |
| `SysModuleApi` | `config-api/.../api/SysModuleApi.java` | 模块 API 接口定义（跨模块调用） |
| `SysFieldApi` | `config-api/.../api/SysFieldApi.java` | 字段 API 接口定义 |
| `ModuleTypeEnum` | `config-api/.../enums/ModuleTypeEnum.java` | LIST / DETAIL 枚举 |
| `SaveModuleReq` | `config-api/.../request/SaveModuleReq.java` | 保存模块请求 DTO（含所有嵌套子结构） |
| `SysModuleCompleteResp` | `config-api/.../response/SysModuleCompleteResp.java` | 模块完整信息响应 DTO |
| `ModuleFieldQueryService` | `shared/.../service/ModuleFieldQueryService.java` | 跨模块字段查询接口 |
| `SysRoleModuleFieldPermission` | `config-biz/.../entity/SysRoleModuleFieldPermission.java` | 角色字段权限实体 |

### 关键数据库表

| 表名 | 作用 |
|------|------|
| `sys_module` | 模块树主表 |
| `sys_module_table` | 模块关联的物理表 |
| `sys_module_simple_field` | 模块单表字段映射 |
| `sys_module_combine_field` | 模块多表组合字段 |
| `sys_module_status` | 模块关联状态（DETAIL） |
| `sys_field` | 全局字段元配置 |
| `sys_field_value` | 字段业务值（按模块） |
| `sys_role_module_field_permission` | 角色字段读写权限 |

---

## 附录：常见修改场景

### 场景 A：新增模块字段属性

1. `SysModule` 实体加字段
2. `SaveSysModuleReq` 加入请求字段
3. `SysModuleCompleteResp.ModuleInfo` / `SysModuleDetailResp` 加入响应字段
4. `SysModuleService.createOrUpdateModule` 和 `updateModuleInfo` 中处理赋值
5. `SysModuleService.toModuleDetail` / `buildModuleCompleteResp` 中处理映射

### 场景 B：为模块添加新的子配置类型

参考 `saveModuleTables` / `saveSimpleFields` 的模式：
1. 新建实体类 + Mapper
2. 在 `SaveModuleReq` 加入对应 List
3. 在 `SysModuleCompleteResp` 加入对应 Info 内部类
4. 在 `SysModuleService.saveModule` 中调用新的 saveXxx 方法
5. 在 `buildModuleCompleteResp` 中填充返回值
6. 在 `deleteModuleComplete` 中加入清理逻辑

### 场景 C：修改字段配置的扩展值逻辑

入口：`SysFieldService.processFieldValues`
当前是全量删除重插，如需改为增量更新，在此方法中实现 Diff 逻辑即可。

### 场景 D：角色字段权限查询/保存

目前权限表 `sys_role_module_field_permission` 已有实体和 Mapper，但尚无对应的独立 Controller。
如需实现权限配置接口，参考 `SaveRoleModuleFieldReq` 和 `RoleModuleFieldResp`，在 config-biz 新建 Controller + Service 即可。
