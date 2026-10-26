# API 和 DTO 命名规则详细参考
项目采用三层架构：API 接口 + Service 实现 + Controller 包装

### 分层职责

| 层级 | 位置 | 返回类型 | 说明 |
|------|------|---------|------|
| API 接口 | `*-api` 模块 | `T`、`List<T>`、`PageResult<T>`、`void` | 定义接口契约，不包装 |
| Service 实现 | `*-biz` 模块 | `T`、`List<T>`、`PageResult<T>`、`void` | 实现 API 接口，不包装 |
| Controller | `*-biz` 模块 | `ApiResponse<T>`、`ApiResponse<List<T>>`、`ApiResponse<PageResult<T>>`、`ApiResponse<Void>` | 注入 API 接口，包装在 ApiResponse 中 |

### 实现模式

**API 接口**（jdec-platform-config-api）:
```java
public interface SysRoleApi {
    RoleEntryResp createRole(CreateRoleReq request);
    List<RoleEntryResp> getAllRoles();
    PageResult<RoleEntryResp> listRoles(Long pageNum, Long pageSize);
    void deleteRole(Long roleId);
}
```

**Service 实现**（jdec-platform-config-biz）:
```java
@Service
public class SysRoleService implements SysRoleApi {
    @Override
    public RoleEntryResp createRole(CreateRoleReq request) {
        // 业务逻辑
        return roleEntryResp;
    }
}
```

**Controller**（jdec-platform-config-biz）:
```java
@RestController
@RequestMapping("/api/config/roles")
public class SysRoleController {
    private final SysRoleApi roleService;
    
    @PostMapping
    public ApiResponse<RoleEntryResp> createRole(@RequestBody CreateRoleReq request) {
        RoleEntryResp result = roleService.createRole(request);
        return ApiResponse.success("创建成功", result);
    }
}
```

---

## 📌 核心原则



### 1. 后缀约定
- **Request DTO**: 后缀为 `Req`（不是 `Request`）
- **Response DTO**: 后缀为 `Resp`（不是 `Response`）
- **API 接口**: 后缀为 `Api`（不是 `Service`）

### 2. 命名风格
- 采用 **PascalCase**（大驼峰）命名
- 名称应该清晰表达意图
- 避免缩写，除非是通用缩写（如 ID、URL）

### 3. 命名一致性原则

**关键原则**: Entity 名称是命名的源头，其他所有层级都应该基于 Entity 名称保持一致。

| 层级 | 命名格式 | 示例（Entity: SysRole） |
|------|---------|----------------------|
| Entity | `{Entity}` | `SysRole` |
| Mapper | `{Entity}Mapper` | `SysRoleMapper` |
| Service | `{Entity}Service` | `SysRoleService` |
| Controller | `{Entity}Controller` | `SysRoleController` |
| API 接口 | `{Entity}Api` | `SysRoleApi` |
| Request DTO | `{Action}{Object}Req` | `CreateRoleReq` |
| Response DTO | `{Object}{Type}Resp` | `RoleEntryResp` |

---

## 📋 Request DTO 命名规则

### 1. CRUD 操作

| 操作 | 命名模式 | 示例 |
|------|---------|------|
| 创建 | `Create{Object}Req` | `CreateRoleReq` |
| 读取 | `Query{Object}Req` | `QueryRoleReq` |
| 更新 | `Update{Object}Req` | `UpdateRoleReq` |
| 删除 | `Delete{Object}Req` | `DeleteRoleReq` |

### 2. 特殊操作

| 操作 | 命名模式 | 示例 |
|------|---------|------|
| 登录 | `{Action}Req` | `LoginReq` |
| 发送 | `Send{Object}Req` | `SendCodeReq` |
| 分配 | `Add{Object}To{Target}Req` | `AddPermissionToRoleReq` |
| 移除 | `Remove{Object}From{Target}Req` | `RemovePermissionFromRoleReq` |
| 配置 | `{Object}ConfigReq` | `ModuleFieldConfigReq` |
| 注册 | `Register{Object}Req` | `RegisterModuleReq` |
| 导入 | `Import{Object}Req` | `ImportUserReq` |
| 导出 | `Export{Object}Req` | `ExportUserReq` |
| 批量 | `Batch{Object}Req` | `BatchDeleteRoleReq` |

---

## 📋 Response DTO 命名规则

### 1. 响应类型

| 类型 | 命名模式 | 示例 | 用途 |
|------|---------|------|------|
| 条目 | `{Object}EntryResp` | `RoleEntryResp` | 列表中的单个条目 |
| 详情 | `{Object}DetailResp` | `RoleDetailResp` | 详情页面，完整信息 |
| 基本信息 | `{Object}BasicInfoResp` | `UserBasicInfoResp` | 最小化字段集 |
| 配置 | `{Object}ConfigResp` | `ModuleFieldConfigResp` | 配置相关 |
| 统计 | `{Object}StatResp` | `UserStatResp` | 统计数据 |
| 树形 | `{Object}TreeResp` | `DepartmentTreeResp` | 树形结构 |

---

## 🔗 API 接口命名规则

### 1. 接口命名

```
Sys{Object}Api              # 系统级别的 API
{Object}Api                 # 业务级别的 API
```

### 2. 方法命名规则

| 操作 | 方法名 | 返回类型 |
|------|--------|---------|
| 获取单个 | `get{Object}ById()` | `{Object}DetailResp` |
| 获取列表 | `getAll{Objects}()` | `List<{Object}EntryResp>` |
| 创建 | `create{Object}()` | `{Object}EntryResp` |
| 更新 | `update{Object}()` | `{Object}EntryResp` |
| 删除 | `delete{Object}()` | `void` |
| 添加关联 | `add{Object}To{Target}()` | `void` |
| 移除关联 | `remove{Object}From{Target}()` | `void` |

---

## 🎯 各场景详细规范

### 场景 1：CRUD 操作

#### Create（创建）

**API 接口**:
```java
public interface SysRoleApi {
    RoleEntryResp createRole(CreateRoleReq request);
}
```

**Service 实现**:
```java
@Service
public class SysRoleService implements SysRoleApi {
    @Override
    public RoleEntryResp createRole(CreateRoleReq request) {
        SysRole role = new SysRole();
        role.setRoleName(request.getRoleName());
        sysRoleMapper.insert(role);
        return roleConverter.toRoleEntry(role);
    }
}
```

**Controller**:
```java
@PostMapping
public ApiResponse<RoleEntryResp> createRole(@RequestBody CreateRoleReq request) {
    RoleEntryResp result = roleService.createRole(request);
    return ApiResponse.success("创建成功", result);
}
```

**HTTP 响应**:
```json
{
  "status": 200,
  "msg": "创建成功",
  "data": {
    "roleId": 1,
    "roleName": "admin"
  }
}
```

#### Read（读取）

**API 接口**:
```java
public interface SysRoleApi {
    RoleDetailResp getRoleById(Long roleId);
}
```

**Service 实现**:
```java
@Service
public class SysRoleService implements SysRoleApi {
    @Override
    public RoleDetailResp getRoleById(Long roleId) {
        SysRole role = sysRoleMapper.selectById(roleId);
        return roleConverter.toRoleDetail(role);
    }
}
```

**Controller**:
```java
@GetMapping("/{roleId}")
public ApiResponse<RoleDetailResp> getRole(@PathVariable Long roleId) {
    RoleDetailResp result = roleService.getRoleById(roleId);
    return ApiResponse.success(result);
}
```

#### Update（更新）

**API 接口**:
```java
public interface SysRoleApi {
    RoleEntryResp updateRole(UpdateRoleReq request);
}
```

**Controller**:
```java
@PutMapping("/{roleId}")
public ApiResponse<RoleEntryResp> updateRole(@RequestBody UpdateRoleReq request) {
    RoleEntryResp result = roleService.updateRole(request);
    return ApiResponse.success("更新成功", result);
}
```

#### Delete（删除）

**API 接口**:
```java
public interface SysRoleApi {
    void deleteRole(Long roleId);
}
```

**Controller**:
```java
@DeleteMapping("/{roleId}")
public ApiResponse<Void> deleteRole(@PathVariable Long roleId) {
    roleService.deleteRole(roleId);
    return ApiResponse.success();
}
```

---

### 场景 2：List 操作（无分页）

**特点**: 返回所有符合条件的数据，无分页信息，适用于数据量较小的场景

#### 简单列表

**API 接口**:
```java
public interface SysRoleApi {
    List<RoleEntryResp> getAllRoles();
}
```

**Controller**:
```java
@GetMapping
public ApiResponse<List<RoleEntryResp>> listRoles() {
    List<RoleEntryResp> result = roleService.getAllRoles();
    return ApiResponse.success(result);
}
```

**HTTP 请求**: `GET /api/config/roles`

**HTTP 响应**:
```json
{
  "status": 200,
  "msg": "ok",
  "data": [
    { "roleId": 1, "roleName": "admin" },
    { "roleId": 2, "roleName": "user" }
  ]
}
```

#### 条件列表

**API 接口**:
```java
public interface SysRoleApi {
    List<RoleEntryResp> queryRoles(RoleQueryReq request);
}
```

**Controller**:
```java
@PostMapping("/query")
public ApiResponse<List<RoleEntryResp>> queryRoles(@RequestBody RoleQueryReq request) {
    List<RoleEntryResp> result = roleService.queryRoles(request);
    return ApiResponse.success(result);
}
```

#### 搜索列表

**API 接口**:
```java
public interface SysRoleApi {
    List<RoleEntryResp> searchRoles(String keyword);
}
```

**Controller**:
```java
@GetMapping("/search")
public ApiResponse<List<RoleEntryResp>> searchRoles(@RequestParam String keyword) {
    List<RoleEntryResp> result = roleService.searchRoles(keyword);
    return ApiResponse.success(result);
}
```

---

### 场景 3：Page 操作（有分页）

**特点**: 返回指定页码的数据，包含分页信息，适用于数据量较大的场景

#### 简单分页

**API 接口**:
```java
public interface SysRoleApi {
    PageResult<RoleEntryResp> listRoles(Long pageNum, Long pageSize);
}
```

**Controller**:
```java
@GetMapping("/page")
public ApiResponse<PageResult<RoleEntryResp>> listRoles(
        @RequestParam(defaultValue = "1") Long pageNum,
        @RequestParam(defaultValue = "10") Long pageSize) {
    PageResult<RoleEntryResp> result = roleService.listRoles(pageNum, pageSize);
    return ApiResponse.success(result);
}
```

**HTTP 请求**: `GET /api/config/roles/page?pageNum=1&pageSize=10`

**HTTP 响应**:
```json
{
  "status": 200,
  "msg": "ok",
  "data": {
    "pageNum": 1,
    "pageSize": 10,
    "total": 100,
    "pages": 10,
    "records": [
      { "roleId": 1, "roleName": "admin" }
    ]
  }
}
```

#### 条件分页

**API 接口**:
```java
public interface SysRoleApi {
    PageResult<RoleEntryResp> queryRoles(RolePageQueryReq request);
}
```

**Controller**:
```java
@PostMapping("/query")
public ApiResponse<PageResult<RoleEntryResp>> queryRoles(@RequestBody RolePageQueryReq request) {
    PageResult<RoleEntryResp> result = roleService.queryRoles(request);
    return ApiResponse.success(result);
}
```

#### 分页搜索

**API 接口**:
```java
public interface SysRoleApi {
    PageResult<RoleEntryResp> searchRoles(Long pageNum, Long pageSize, String keyword);
}
```

**Controller**:
```java
@GetMapping("/search")
public ApiResponse<PageResult<RoleEntryResp>> searchRoles(
        @RequestParam(defaultValue = "1") Long pageNum,
        @RequestParam(defaultValue = "10") Long pageSize,
        @RequestParam String keyword) {
    PageResult<RoleEntryResp> result = roleService.searchRoles(pageNum, pageSize, keyword);
    return ApiResponse.success(result);
}
```

---

## 📊 List vs Page 对比

### 使用场景对比

| 场景 | List | Page |
|------|------|------|
| 数据量小（< 100） | ✅ 推荐 | ❌ 不需要 |
| 数据量大（> 100） | ❌ 不推荐 | ✅ 推荐 |
| 需要显示总数 | ❌ 无法显示 | ✅ 可以显示 |
| 需要跳页 | ❌ 无法跳页 | ✅ 可以跳页 |
| 前端需要分页控件 | ❌ 不需要 | ✅ 需要 |

---

## 📋 完整场景对照表

### CRUD 操作

| 操作 | Request | Response | API 方法 | HTTP | 路由 |
|------|---------|----------|---------|------|------|
| 创建 | `CreateRoleReq` | `RoleEntryResp` | `createRole()` | POST | `/api/config/roles` |
| 读取 | - | `RoleDetailResp` | `getRoleById()` | GET | `/api/config/roles/{id}` |
| 更新 | `UpdateRoleReq` | `RoleEntryResp` | `updateRole()` | PUT | `/api/config/roles/{id}` |
| 删除 | - | `Void` | `deleteRole()` | DELETE | `/api/config/roles/{id}` |

### List 操作（无分页）

| 操作 | Request | Response | API 方法 | HTTP | 路由 |
|------|---------|----------|---------|------|------|
| 简单列表 | - | `List<RoleEntryResp>` | `getAllRoles()` | GET | `/api/config/roles` |
| 条件列表 | `RoleQueryReq` | `List<RoleEntryResp>` | `queryRoles()` | POST | `/api/config/roles/query` |
| 搜索列表 | - | `List<RoleEntryResp>` | `searchRoles()` | GET | `/api/config/roles/search` |

### Page 操作（有分页）

| 操作 | Request | Response | API 方法 | HTTP | 路由 |
|------|---------|----------|---------|------|------|
| 简单分页 | - | `PageResult<RoleEntryResp>` | `listRoles()` | GET | `/api/config/roles/page` |
| 条件分页 | `RolePageQueryReq` | `PageResult<RoleEntryResp>` | `queryRoles()` | POST | `/api/config/roles/query` |
| 分页搜索 | - | `PageResult<RoleEntryResp>` | `searchRoles()` | GET | `/api/config/roles/search` |

---

## ✅ 场景选择决策树

```
需要获取数据？
├─ 单个对象？
│  └─ getRoleById() → RoleDetailResp
│
├─ 多个对象？
│  ├─ 数据量小（< 100）？
│  │  ├─ 无条件？ → getAllRoles() → List<RoleEntryResp>
│  │  ├─ 有条件？ → queryRoles() → List<RoleEntryResp>
│  │  └─ 搜索？ → searchRoles() → List<RoleEntryResp>
│  │
│  └─ 数据量大（> 100）？
│     ├─ 无条件？ → listRoles(pageNum, pageSize) → PageResult<RoleEntryResp>
│     ├─ 有条件？ → queryRoles(RolePageQueryReq) → PageResult<RoleEntryResp>
│     └─ 搜索？ → searchRoles(pageNum, pageSize, keyword) → PageResult<RoleEntryResp>
│
├─ 创建对象？ → createRole(CreateRoleReq) → RoleEntryResp
├─ 更新对象？ → updateRole(UpdateRoleReq) → RoleEntryResp
└─ 删除对象？ → deleteRole(roleId) → void
```

---

## 🚫 常见错误

### DTO 命名错误

| ❌ 错误 | ✅ 正确 | 说明 |
|--------|--------|------|
| `LoginRequest` | `LoginReq` | 后缀应该是 `Req` |
| `UserResponse` | `UserResp` | 后缀应该是 `Resp` |
| `UserReq` | `CreateUserReq` | 应该包含操作动词 |
| `DataResp` | `UserEntryResp` | 应该包含对象名称和类型 |
| `RoleCreateReq` | `CreateRoleReq` | 动词应该在前 |

### List vs Page 错误

| ❌ 错误 | ✅ 正确 | 说明 |
|--------|--------|------|
| 数据量大时使用 `List<T>` | 使用 `PageResult<T>` | 大数据量应该使用分页 |
| 数据量小时使用 `PageResult<T>` | 使用 `List<T>` | 小数据量无需分页 |
| `getRoleList()` | `listRoles()` | 应该使用 `list` 前缀 |
| `getRolesByPage()` | `listRoles(pageNum, pageSize)` | 方法名不需要包含 "ByPage" |
| `RolePageResp` | `PageResult<RoleEntryResp>` | 应该使用 `PageResult` |

---

## 📚 参考资源

### 现有模块示例

**Auth 模块**:
- `LoginReq` / `LoginResp`
- `SendCodeReq`
- `EmployeeInfoResp`

**Config 模块**:
- `CreateRoleReq` / `RoleEntryResp`
- `UpdateRoleReq`
- `AddPermissionToRoleReq`
- `ModuleFieldConfigReq` / `ModuleFieldConfigResp`

**HR 模块**:
- `UserBasicInfoResp`
- `UserBaseResp`


