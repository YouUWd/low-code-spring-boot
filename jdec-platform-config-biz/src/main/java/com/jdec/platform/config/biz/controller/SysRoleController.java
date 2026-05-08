package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysRoleApi;
import com.jdec.platform.config.api.dto.request.QueryRoleConfigPageReq;
import com.jdec.platform.config.api.dto.request.SaveRoleUserReq;
import com.jdec.platform.config.api.dto.response.CheckRoleUserResp;
import com.jdec.platform.config.api.dto.response.RoleConfigDetailResp;
import com.jdec.platform.config.api.dto.response.RoleConfigPageResp;
import com.jdec.platform.config.api.dto.response.RoleUserTimeResp;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 角色管理 REST 控制器
 *
 * <p>提供角色的增删改查接口。
 */
@RestController
@RequestMapping("/api/config/role")
@RequiredArgsConstructor
@Tag(name = "角色管理", description = "角色的增删改查接口")
public class SysRoleController {

    private final SysRoleApi sysRoleApi;

    @PostMapping("/check-role-user")
    @Operation(summary = "保存角色及用户关联预检查", description = "检查是否存在冲突，返回需要确认的提示信息")
    public ApiResponse<CheckRoleUserResp> checkRoleUser(@RequestBody SaveRoleUserReq request) {
        return ApiResponse.success(sysRoleApi.checkRoleUser(request));
    }

    @PostMapping("/save-role-user")
    @Operation(summary = "保存角色及用户关联", description = "保存角色信息及其用户关联，id为空则新增，不为空则修改。force=true时强制覆盖")
    public ApiResponse<Void> saveRoleUser(@RequestBody SaveRoleUserReq request) {
        sysRoleApi.saveRoleUser(request);
        return ApiResponse.success();
    }

    @GetMapping("/get-role-user-time")
    @Operation(summary = "获取指定用户的时效")
    public ApiResponse<RoleUserTimeResp> getRoleUserTime(@RequestParam("userId") String userId, @RequestParam("roleId")Long roleId) {
        return ApiResponse.success(sysRoleApi.getRoleUserTime(userId,roleId));
    }

    @GetMapping("/get-role-config-page")
    @Operation(summary = "分页查询角色配置", description = "分页查询角色及其关联的用户、主体列表")
    public ApiResponse<PageResult<RoleConfigPageResp>> getRoleConfigPage(QueryRoleConfigPageReq query) {
        return ApiResponse.success(sysRoleApi.getRoleConfigPage(query));
    }


    @GetMapping("/get-role-config-detail")
    @Operation(summary = "获取角色配置详情", description = "获取角色及其关联的用户、主体列表")
    public ApiResponse<RoleConfigDetailResp> getInsideInfo(
            @Parameter(description = "角色ID", example = "1")
            @RequestParam Long roleId,
            @Parameter(description = "用户类型", example = "1")
            @RequestParam Integer userType
    ) {
        return ApiResponse.success(sysRoleApi.getRoleConfigDetail(roleId,userType));
    }


    @DeleteMapping("/{roleId}")
    @Operation(summary = "删除角色", description = "删除指定角色及其关联的用户/主体关系")
    public ApiResponse<Void> deleteRole(
            @Parameter(description = "角色ID", example = "1")
            @PathVariable Long roleId) {
        sysRoleApi.deleteRole(roleId);
        return ApiResponse.success();
    }
}
