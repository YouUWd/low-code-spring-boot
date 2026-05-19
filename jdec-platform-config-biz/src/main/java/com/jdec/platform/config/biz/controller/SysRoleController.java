package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysRoleApi;
import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.dto.request.QueryRoleConfigPageReq;
import com.jdec.platform.config.api.dto.request.SaveRoleUserReq;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.hr.api.UserApi;
import com.jdec.platform.hr.api.dto.response.UserTreeNodeResp;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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
}
