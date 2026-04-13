package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.biz.entity.SysRolePermission;
import com.jdec.platform.config.biz.service.SysRolePermissionService;
import com.jdec.platform.shared.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 角色权限管理 REST 控制器 */
@RestController
@RequestMapping("/api/config/role-permission")
@RequiredArgsConstructor
public class SysRolePermissionController {

    private final SysRolePermissionService rolePermissionService;

    // ==================== 角色权限管理 ====================

    /** 为角色添加权限 POST /api/config/role-permission */
    @PostMapping
    public ApiResponse<SysRolePermission> addPermissionToRole(
            @RequestParam Long roleId, @RequestParam Long permissionId) {
        try {
            SysRolePermission rolePermission =
                    rolePermissionService.addPermissionToRole(roleId, permissionId);
            return ApiResponse.success("权限添加成功", rolePermission);
        } catch (Exception e) {
            return ApiResponse.error(500, "权限添加失败: " + e.getMessage());
        }
    }

    /** 根据角色 ID 查询权限 GET /api/config/role-permission/role/{roleId} */
    @GetMapping("/role/{roleId}")
    public ApiResponse<List<SysRolePermission>> getPermissionsByRoleId(@PathVariable Long roleId) {
        List<SysRolePermission> rolePermissions =
                rolePermissionService.getPermissionsByRoleId(roleId);
        return ApiResponse.success(rolePermissions);
    }

    /** 根据权限 ID 查询角色 GET /api/config/role-permission/permission/{permissionId} */
    @GetMapping("/permission/{permissionId}")
    public ApiResponse<List<SysRolePermission>> getRolesByPermissionId(
            @PathVariable Long permissionId) {
        List<SysRolePermission> rolePermissions =
                rolePermissionService.getRolesByPermissionId(permissionId);
        return ApiResponse.success(rolePermissions);
    }

    /** 检查角色是否拥有权限 GET /api/config/role-permission/check */
    @GetMapping("/check")
    public ApiResponse<Boolean> hasPermission(
            @RequestParam Long roleId, @RequestParam Long permissionId) {
        boolean hasPermission = rolePermissionService.hasPermission(roleId, permissionId);
        return ApiResponse.success(hasPermission);
    }

    /** 从角色移除权限 DELETE /api/config/role-permission */
    @DeleteMapping
    public ApiResponse<Void> removePermissionFromRole(
            @RequestParam Long roleId, @RequestParam Long permissionId) {
        try {
            rolePermissionService.removePermissionFromRole(roleId, permissionId);
            return ApiResponse.success();
        } catch (Exception e) {
            return ApiResponse.error(500, "权限移除失败: " + e.getMessage());
        }
    }

    /** 删除角色的所有权限 DELETE /api/config/role-permission/role/{roleId} */
    @DeleteMapping("/role/{roleId}")
    public ApiResponse<Void> deletePermissionsByRoleId(@PathVariable Long roleId) {
        try {
            rolePermissionService.deletePermissionsByRoleId(roleId);
            return ApiResponse.success();
        } catch (Exception e) {
            return ApiResponse.error(500, "角色权限删除失败: " + e.getMessage());
        }
    }
}
