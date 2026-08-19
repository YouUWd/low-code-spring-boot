package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysRoleApi;
import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.dto.request.QueryRoleConfigPageReq;
import com.jdec.platform.config.api.dto.request.SaveRoleUserReq;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.hr.api.DepartmentApi;
import com.jdec.platform.hr.api.UserApi;
import com.jdec.platform.hr.api.bo.DepartmentBO;
import com.jdec.platform.hr.api.dto.response.UserTreeNodeResp;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 角色管理 REST 控制器
 *
 * <p>提供角色的增删改查接口。
 */
@RestController
@RequestMapping("/api/config/sys/role")
@RequiredArgsConstructor
@Tag(name = "角色管理", description = "角色的增删改查接口")
public class SysRoleController {

    private final SysRoleApi sysRoleApi;
    private final SysUserApi sysUserApi;
    private final UserApi userApi;
    private final DepartmentApi departmentApi;

    @GetMapping("/get-user-tree")
    @Operation(summary = "获取人员架构树", description = "按照部门层级展示人员架构")
    public ApiResponse<List<UserTreeNodeResp>> getUserTreeList(
            @Parameter(description = "用户类型", example = "1") @RequestParam Integer userType) {
        return ApiResponse.success(userApi.getUserTreeList(userType));
    }

    @GetMapping("/get-user-tree-for-search")
    @Operation(summary = "获取人员架构树（用于下拉搜索）", description = "按照部门层级展示人员架构，仅包含 sys_user 表中存在的用户")
    public ApiResponse<List<UserSearchTreeNodeResp>> getUserTreeListForSearch(
            @Parameter(description = "用户类型", example = "1") @RequestParam Integer userType) {
        return ApiResponse.success(sysUserApi.getUserTreeListForSearch(userType));
    }

    @PostMapping("/check-role-user")
    @Operation(summary = "保存角色及用户关联预检查", description = "检查是否存在冲突，返回需要确认的提示信息")
    public ApiResponse<CheckRoleUserResp> checkRoleUser(@RequestBody SaveRoleUserReq request) {
        return ApiResponse.success(sysRoleApi.checkRoleUser(request));
    }

    @PostMapping("/save-role-user")
    @Operation(summary = "保存角色及用户关联", description = "保存角色信息及其用户关联，id为空则新增，不为空则修改。force=true时强制覆盖")
    public ApiResponse<RoleConfigPageResp> saveRoleUser(@RequestBody SaveRoleUserReq request) {
        return ApiResponse.success(sysRoleApi.saveRoleUser(request));
    }

    @GetMapping("/get-role-user-time")
    @Operation(summary = "获取指定用户的时效")
    public ApiResponse<RoleUserTimeResp> getRoleUserTime(
            @RequestParam("userId") String userId, @RequestParam("roleId") Long roleId) {
        return ApiResponse.success(sysRoleApi.getRoleUserTime(userId, roleId));
    }

    @GetMapping("/get-role-config-page")
    @Operation(summary = "分页查询角色配置", description = "分页查询角色及其关联的用户、主体列表")
    public ApiResponse<PageResult<RoleConfigPageResp>> getRoleConfigPage(
            QueryRoleConfigPageReq query) {
        return ApiResponse.success(sysRoleApi.getRoleConfigPage(query));
    }

    @GetMapping("/get-role-config-detail")
    @Operation(summary = "获取角色配置详情", description = "获取角色及其关联的用户、主体列表")
    public ApiResponse<RoleConfigDetailResp> getInsideInfo(
            @Parameter(description = "角色ID", example = "1") @RequestParam Long roleId) {
        return ApiResponse.success(sysRoleApi.getRoleConfigDetail(roleId));
    }

    @GetMapping("/get-role-config-detail-all")
    @Operation(summary = "获取角色配置详情（包含所有非删除状态）", description = "获取角色及其关联的用户、主体列表，包括待生效的数据，不过滤时效")
    public ApiResponse<RoleConfigDetailResp> getRoleConfigDetailAll(
            @Parameter(description = "角色ID", example = "1") @RequestParam Long roleId) {
        return ApiResponse.success(sysRoleApi.getRoleConfigDetailAll(roleId));
    }

    @DeleteMapping("/{roleId}")
    @Operation(summary = "删除角色", description = "删除指定角色及其关联的用户/主体关系")
    public ApiResponse<Void> deleteRole(
            @Parameter(description = "角色ID", example = "1") @PathVariable Long roleId) {
        sysRoleApi.deleteRole(roleId);
        return ApiResponse.success();
    }

    /** 提供主体下拉列表 */
    @GetMapping("/subjects")
    @Operation(summary = "主体下拉列表")
    public ApiResponse<List<SubjectOptionResp>> listSubjects() {
        return ApiResponse.success(sysRoleApi.listSubjectOptions());
    }

    @GetMapping("/get-list")
    @Operation(summary = "角色下拉列表")
    public ApiResponse<List<RoleListResp>> listRole() {
        return ApiResponse.success(sysRoleApi.listRole());
    }

    @GetMapping("/get-user-roles")
    @Operation(summary = "根据用户ID和主体ID查询用户角色列表", description = "查询用户有哪些角色，仅返回在有效期内的角色")
    public ApiResponse<List<UserRoleListResp>> getUserRolesByUserIdAndSubjectId(
            @Parameter(description = "用户ID", example = "1") @RequestParam Long userId) {
        return ApiResponse.success(sysRoleApi.getUserRolesByUserIdAndSubjectId(userId));
    }

    @GetMapping("/get-user-switch-tree")
    @Operation(summary = "获取用户切换树形结构", description = "用于页面右上角用户切换，根据主体ID和项目编码查询角色关联的用户树形结构")
    public ApiResponse<List<UserSearchTreeNodeResp>> getUserSwitchTree(
            @Parameter(description = "用户类型", example = "1") @RequestParam Integer userType,
            @Parameter(description = "用户名称（模糊查询）", required = false) @RequestParam(required = false)
                    String userName) {
        // userType: 0=全部, 1=内部, 2=外部。当前项目仅支持内部用户树
        if (userType != null && userType == 2) {
            return ApiResponse.success(Collections.emptyList());
        }

        // 1. 通过 API 层查询用户数据（sys_user 已在角色保存时同步主体下用户，无需再调 HR）
        UserSwitchDataResp data = sysRoleApi.getUserSwitchData(userName);
        List<UserSwitchDataResp.SysUserInfo> sysUsers = data.getSysUsers();

        // 2. 构建用户节点（去重）
        Map<Long, UserSearchTreeNodeResp> userMap = new LinkedHashMap<>();
        for (UserSwitchDataResp.SysUserInfo user : sysUsers) {
            String deptIds = user.getDepartIds();
            if (deptIds == null || deptIds.isEmpty()) {
                continue;
            }
            for (String deptId : deptIds.split(",")) {
                deptId = deptId.trim();
                if (deptId.isEmpty()) {
                    continue;
                }
                UserSearchTreeNodeResp userNode =
                        UserSearchTreeNodeResp.builder()
                                .id("U" + user.getUserId())
                                .sysId(user.getId())
                                .label(user.getUserName())
                                .departId("D" + deptId)
                                .sex(user.getSex() != null ? user.getSex() : 1)
                                .avatar(user.getUserAvatar())
                                .workNumber(
                                        user.getWorkNumber() != null ? user.getWorkNumber() : "")
                                .type(3)
                                .departName(
                                        user.getDepartName() != null ? user.getDepartName() : "")
                                .subjectId(user.getSubjectId())
                                .build();
                userMap.put(user.getUserId(), userNode);
            }
        }

        // 3. 将用户按部门分类
        Map<String, List<UserSearchTreeNodeResp>> deptUsers = new LinkedHashMap<>();
        for (UserSearchTreeNodeResp userNode : userMap.values()) {
            deptUsers.computeIfAbsent(userNode.getDepartId(), k -> new ArrayList<>()).add(userNode);
        }

        // 4. 从HR模块获取部门列表
        List<DepartmentBO> departmentList = departmentApi.getDepartmentList();

        // 5. 构建部门节点并挂载用户
        List<UserSearchTreeNodeResp> departListWithUsers = new ArrayList<>();
        for (DepartmentBO dept : departmentList) {
            String pid = (dept.getPid() != null && dept.getPid() != 0) ? "D" + dept.getPid() : "D0";
            UserSearchTreeNodeResp node =
                    UserSearchTreeNodeResp.builder()
                            .id("D" + dept.getId())
                            .label(dept.getName())
                            .pid(pid)
                            .type(dept.getType() != null ? dept.getType() : 1)
                            .children(
                                    new ArrayList<>(
                                            deptUsers.getOrDefault(
                                                    "D" + dept.getId(), new ArrayList<>())))
                            .build();
            departListWithUsers.add(node);
        }

        // 6. 构建嵌套树
        List<UserSearchTreeNodeResp> tree = buildNestedTree(departListWithUsers, "D0");

        // 7. 过滤掉没有用户的部门
        return ApiResponse.success(filterEmptyDepartments(tree));
    }

    /**
     * 构建嵌套树结构
     *
     * @param items 节点列表
     * @param rootPid 根节点父ID
     * @return 树形结构
     */
    private List<UserSearchTreeNodeResp> buildNestedTree(
            List<UserSearchTreeNodeResp> items, String rootPid) {
        Map<String, UserSearchTreeNodeResp> nodeMap = new LinkedHashMap<>();
        for (UserSearchTreeNodeResp item : items) {
            nodeMap.put(item.getId(), item);
        }

        List<UserSearchTreeNodeResp> tree = new ArrayList<>();
        for (UserSearchTreeNodeResp item : items) {
            String pid = item.getPid();
            UserSearchTreeNodeResp parent = nodeMap.get(pid);
            boolean isRoot = (pid == null || pid.equals(rootPid) || parent == null);
            if (isRoot) {
                tree.add(item);
            } else {
                List<UserSearchTreeNodeResp> children = parent.getChildren();
                if (children == null) {
                    children = new ArrayList<>();
                    parent.setChildren(children);
                }
                children.add(item);
            }
        }
        return tree;
    }

    /**
     * 过滤掉没有用户的部门节点
     *
     * @param nodes 树形节点列表
     * @return 过滤后的节点列表
     */
    private List<UserSearchTreeNodeResp> filterEmptyDepartments(
            List<UserSearchTreeNodeResp> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return nodes;
        }

        List<UserSearchTreeNodeResp> filtered = new ArrayList<>();
        for (UserSearchTreeNodeResp node : nodes) {
            if (hasUsers(node)) {
                // 递归过滤子节点
                if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                    node.setChildren(filterEmptyDepartments(node.getChildren()));
                }
                filtered.add(node);
            }
        }
        return filtered;
    }

    /**
     * 检查节点是否包含用户
     *
     * @param node 树形节点
     * @return 是否包含用户
     */
    private boolean hasUsers(UserSearchTreeNodeResp node) {
        // type=3 表示用户节点
        if (node.getType() == 3) {
            return true;
        }

        // 检查子节点
        List<UserSearchTreeNodeResp> children = node.getChildren();
        if (children == null || children.isEmpty()) {
            return false;
        }

        // 递归检查子节点是否有用户
        for (UserSearchTreeNodeResp child : children) {
            if (hasUsers(child)) {
                return true;
            }
        }
        return false;
    }
}
