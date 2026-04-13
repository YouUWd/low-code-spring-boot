package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.dto.response.RoleEntryResp;
import com.jdec.platform.config.biz.converter.PermissionConverter;
import com.jdec.platform.config.biz.entity.SysRole;
import com.jdec.platform.config.biz.service.SysRoleService;
import com.jdec.platform.shared.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 角色管理 REST 控制器 */
@RestController
@RequestMapping("/api/config/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;
    private final PermissionConverter permissionConverter;

    /** 创建角色 POST /api/config/role */
    @PostMapping
    public ApiResponse<RoleEntryResp> createRole(@RequestBody RoleEntryResp request) {
        try {
            SysRole role = permissionConverter.toSysRole(request);
            SysRole created = roleService.createRole(role);
            return ApiResponse.success("角色创建成功", permissionConverter.toRoleEntry(created));
        } catch (Exception e) {
            return ApiResponse.error(500, "角色创建失败: " + e.getMessage());
        }
    }

    /** 根据 ID 获取角色 GET /api/config/role/{id} */
    @GetMapping("/{id}")
    public ApiResponse<RoleEntryResp> getRoleById(@PathVariable Long id) {
        SysRole role = roleService.getRoleById(id);
        if (role == null) {
            return ApiResponse.error(404, "角色不存在");
        }
        return ApiResponse.success(permissionConverter.toRoleEntry(role));
    }

    /** 根据角色编码获取角色 GET /api/config/role/code/{roleCode} */
    @GetMapping("/code/{roleCode}")
    public ApiResponse<RoleEntryResp> getRoleByCode(@PathVariable String roleCode) {
        SysRole role = roleService.getRoleByCode(roleCode);
        if (role == null) {
            return ApiResponse.error(404, "角色不存在");
        }
        return ApiResponse.success(permissionConverter.toRoleEntry(role));
    }

    /** 获取所有角色 GET /api/config/role */
    @GetMapping
    public ApiResponse<List<RoleEntryResp>> getAllRoles() {
        List<SysRole> roles = roleService.getAllRoles();
        return ApiResponse.success(permissionConverter.toRoleEntries(roles));
    }

    /** 获取启用的角色 GET /api/config/role/enabled */
    @GetMapping("/enabled")
    public ApiResponse<List<RoleEntryResp>> getEnabledRoles() {
        List<SysRole> roles = roleService.getEnabledRoles();
        return ApiResponse.success(permissionConverter.toRoleEntries(roles));
    }

    /** 更新角色 PUT /api/config/role/{id} */
    @PutMapping("/{id}")
    public ApiResponse<RoleEntryResp> updateRole(
            @PathVariable Long id, @RequestBody RoleEntryResp request) {
        try {
            SysRole role = permissionConverter.toSysRole(request);
            role.setId(id);
            SysRole updated = roleService.updateRole(role);
            return ApiResponse.success("角色更新成功", permissionConverter.toRoleEntry(updated));
        } catch (Exception e) {
            return ApiResponse.error(500, "角色更新失败: " + e.getMessage());
        }
    }

    /** 删除角色 DELETE /api/config/role/{id} */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        try {
            roleService.deleteRole(id);
            return ApiResponse.success();
        } catch (Exception e) {
            return ApiResponse.error(500, "角色删除失败: " + e.getMessage());
        }
    }
}
