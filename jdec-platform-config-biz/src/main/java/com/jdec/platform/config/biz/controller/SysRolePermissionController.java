package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.dto.request.AddPermissionToRoleReq;
import com.jdec.platform.config.biz.entity.SysRolePermission;
import com.jdec.platform.config.biz.service.SysRolePermissionService;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色权限关联 REST 控制器
 *
 * <p>提供角色与权限的关联管理接口。
 */
@RestController
@RequestMapping("/api/config/role-permissions")
@RequiredArgsConstructor
@Tag(name = "角色权限关联", description = "角色与权限的关联管理接口")
public class SysRolePermissionController {

    private final SysRolePermissionService rolePermissionService;

    /**
     * 根据角色 ID 查询权限列表
     *
     * @param roleId 角色 ID
     * @return 权限列表
     */
    @GetMapping("/roles/{roleId}")
    @Operation(summary = "查询角色的权限", description = "根据角色 ID 查询该角色拥有的所有权限")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功"),
    })
    public ApiResponse<List<SysRolePermission>> listPermissionsByRole(
            @Parameter(description = "角色 ID", example = "1")
            @PathVariable Long roleId) {
        List<SysRolePermission> rolePermissions =
                rolePermissionService.getPermissionsByRoleId(roleId);
        return ApiResponse.success(rolePermissions);
    }

    /**
     * 为角色添加权限
     *
     * @param request 添加权限请求
     * @return 添加结果
     */
    @PostMapping
    @Operation(summary = "为角色添加权限", description = "为指定角色添加权限")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "添加成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
    })
    public ApiResponse<SysRolePermission> addPermissionToRole(@RequestBody AddPermissionToRoleReq request) {
        SysRolePermission rolePermission =
                rolePermissionService.addPermissionToRole(request.getRoleId(), request.getPermissionId());
        return ApiResponse.success("权限添加成功", rolePermission);
    }

    /**
     * 从角色移除权限
     *
     * @param request 移除权限请求
     * @return 移除结果
     */
    @DeleteMapping
    @Operation(summary = "从角色移除权限", description = "从指定角色移除权限")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "移除成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
    })
    public ApiResponse<Void> removePermissionFromRole(@RequestBody AddPermissionToRoleReq request) {
        rolePermissionService.removePermissionFromRole(request.getRoleId(), request.getPermissionId());
        return ApiResponse.success();
    }
}
