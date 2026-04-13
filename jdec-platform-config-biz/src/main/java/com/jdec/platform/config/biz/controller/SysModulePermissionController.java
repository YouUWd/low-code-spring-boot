package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.dto.response.ModulePermissionResp;
import com.jdec.platform.config.biz.converter.PermissionConverter;
import com.jdec.platform.config.biz.entity.SysModulePermission;
import com.jdec.platform.config.biz.service.SysModulePermissionService;
import com.jdec.platform.shared.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 权限管理 REST 控制器 */
@RestController
@RequestMapping("/api/config/permission")
@RequiredArgsConstructor
public class SysModulePermissionController {

    private final SysModulePermissionService permissionService;
    private final PermissionConverter permissionConverter;

    /** 创建权限 POST /api/config/permission */
    @PostMapping
    public ApiResponse<ModulePermissionResp> createPermission(
            @RequestBody SysModulePermission request) {
        try {
            SysModulePermission created = permissionService.createPermission(request);
            return ApiResponse.success(
                    "权限创建成功", permissionConverter.toModulePermissionResp(created));
        } catch (Exception e) {
            return ApiResponse.error(500, "权限创建失败: " + e.getMessage());
        }
    }

    /** 根据 ID 获取权限 GET /api/config/permission/{id} */
    @GetMapping("/{id}")
    public ApiResponse<ModulePermissionResp> getPermissionById(@PathVariable Long id) {
        SysModulePermission permission = permissionService.getPermissionById(id);
        if (permission == null) {
            return ApiResponse.error(404, "权限不存在");
        }
        return ApiResponse.success(permissionConverter.toModulePermissionResp(permission));
    }

    /** 根据权限编码获取权限 GET /api/config/permission/code/{permissionCode} */
    @GetMapping("/code/{permissionCode}")
    public ApiResponse<ModulePermissionResp> getPermissionByCode(
            @PathVariable String permissionCode) {
        SysModulePermission permission = permissionService.getPermissionByCode(permissionCode);
        if (permission == null) {
            return ApiResponse.error(404, "权限不存在");
        }
        return ApiResponse.success(permissionConverter.toModulePermissionResp(permission));
    }

    /** 根据模块 ID 获取权限 GET /api/config/permission/module/{moduleId} */
    @GetMapping("/module/{moduleId}")
    public ApiResponse<List<ModulePermissionResp>> getPermissionsByModuleId(
            @PathVariable Long moduleId) {
        List<SysModulePermission> permissions =
                permissionService.getPermissionsByModuleId(moduleId);
        return ApiResponse.success(permissionConverter.toModulePermissionResps(permissions));
    }

    /** 根据模块 ID 和权限类型获取权限 GET /api/config/permission/module/{moduleId}/type/{type} */
    @GetMapping("/module/{moduleId}/type/{type}")
    public ApiResponse<List<ModulePermissionResp>> getPermissionsByModuleIdAndType(
            @PathVariable Long moduleId, @PathVariable String type) {
        List<SysModulePermission> permissions =
                permissionService.getPermissionsByModuleIdAndType(moduleId, type);
        return ApiResponse.success(permissionConverter.toModulePermissionResps(permissions));
    }

    /** 获取所有权限 GET /api/config/permission */
    @GetMapping
    public ApiResponse<List<ModulePermissionResp>> getAllPermissions() {
        List<SysModulePermission> permissions = permissionService.getAllPermissions();
        return ApiResponse.success(permissionConverter.toModulePermissionResps(permissions));
    }

    /** 更新权限 PUT /api/config/permission/{id} */
    @PutMapping("/{id}")
    public ApiResponse<ModulePermissionResp> updatePermission(
            @PathVariable Long id, @RequestBody SysModulePermission request) {
        try {
            request.setId(id);
            SysModulePermission updated = permissionService.updatePermission(request);
            return ApiResponse.success(
                    "权限更新成功", permissionConverter.toModulePermissionResp(updated));
        } catch (Exception e) {
            return ApiResponse.error(500, "权限更新失败: " + e.getMessage());
        }
    }

    /** 删除权限 DELETE /api/config/permission/{id} */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePermission(@PathVariable Long id) {
        try {
            permissionService.deletePermission(id);
            return ApiResponse.success();
        } catch (Exception e) {
            return ApiResponse.error(500, "权限删除失败: " + e.getMessage());
        }
    }
}
