package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysUserRoleApi;
import com.jdec.platform.config.api.dto.response.UserRoleEntryResp;
import com.jdec.platform.shared.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 用户角色关联 REST 控制器 */
@RestController
@RequestMapping("/api/config/user-role")
@RequiredArgsConstructor
public class SysUserRoleController {

    private final SysUserRoleApi userRoleService;

    /** 为用户添加角色 POST /api/config/user-role/{userId}/{roleId} */
    @PostMapping("/{userId}/{roleId}")
    public ApiResponse<UserRoleEntryResp> addRoleToUser(
            @PathVariable Long userId, @PathVariable Long roleId) {
        try {
            UserRoleEntryResp result = userRoleService.addRoleToUser(userId, roleId);
            return ApiResponse.success("角色添加成功", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "角色添加失败");
        }
    }

    /** 根据用户 ID 查询角色 GET /api/config/user-role/user/{userId} */
    @GetMapping("/user/{userId}")
    public ApiResponse<List<UserRoleEntryResp>> getRolesByUserId(@PathVariable Long userId) {
        try {
            List<UserRoleEntryResp> roles = userRoleService.getRolesByUserId(userId);
            return ApiResponse.success(roles);
        } catch (Exception e) {
            return ApiResponse.error(500, "查询用户角色失败");
        }
    }

    /** 根据角色 ID 查询用户 GET /api/config/user-role/role/{roleId} */
    @GetMapping("/role/{roleId}")
    public ApiResponse<List<UserRoleEntryResp>> getUsersByRoleId(@PathVariable Long roleId) {
        try {
            List<UserRoleEntryResp> users = userRoleService.getUsersByRoleId(roleId);
            return ApiResponse.success(users);
        } catch (Exception e) {
            return ApiResponse.error(500, "查询角色用户失败");
        }
    }

    /** 检查用户是否拥有角色 GET /api/config/user-role/{userId}/{roleId}/check */
    @GetMapping("/{userId}/{roleId}/check")
    public ApiResponse<Boolean> hasRole(@PathVariable Long userId, @PathVariable Long roleId) {
        try {
            boolean hasRole = userRoleService.hasRole(userId, roleId);
            return ApiResponse.success(hasRole);
        } catch (Exception e) {
            return ApiResponse.error(500, "检查用户角色失败");
        }
    }

    /** 从用户移除角色 DELETE /api/config/user-role/{userId}/{roleId} */
    @DeleteMapping("/{userId}/{roleId}")
    public ApiResponse<Void> removeRoleFromUser(
            @PathVariable Long userId, @PathVariable Long roleId) {
        try {
            userRoleService.removeRoleFromUser(userId, roleId);
            return ApiResponse.success();
        } catch (Exception e) {
            return ApiResponse.error(500, "角色移除失败");
        }
    }

    /** 删除用户的所有角色 DELETE /api/config/user-role/user/{userId} */
    @DeleteMapping("/user/{userId}")
    public ApiResponse<Void> deleteRolesByUserId(@PathVariable Long userId) {
        try {
            userRoleService.deleteRolesByUserId(userId);
            return ApiResponse.success();
        } catch (Exception e) {
            return ApiResponse.error(500, "用户角色删除失败");
        }
    }

    /** 删除角色的所有用户关联 DELETE /api/config/user-role/role/{roleId} */
    @DeleteMapping("/role/{roleId}")
    public ApiResponse<Void> deleteUsersByRoleId(@PathVariable Long roleId) {
        try {
            userRoleService.deleteUsersByRoleId(roleId);
            return ApiResponse.success();
        } catch (Exception e) {
            return ApiResponse.error(500, "角色用户删除失败");
        }
    }
}
