package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysPermissionConfigApi;
import com.jdec.platform.config.api.dto.request.CreateSysPermissionConfigReq;
import com.jdec.platform.config.api.dto.request.QuerySysPermissionConfigReq;
import com.jdec.platform.config.api.dto.request.UpdateSysPermissionConfigReq;
import com.jdec.platform.config.api.dto.response.SysPermissionConfigDetailResp;
import com.jdec.platform.config.api.dto.response.SysPermissionConfigEntryResp;
import com.jdec.platform.shared.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限配置管理 Controller
 */
@RestController
@RequestMapping("/api/config/permissions")
@RequiredArgsConstructor
public class SysPermissionConfigController {

    private final SysPermissionConfigApi sysPermissionConfigService;

    /**
     * 创建权限配置
     */
    @PostMapping
    public ApiResponse<SysPermissionConfigEntryResp> createPermissionConfig(@RequestBody CreateSysPermissionConfigReq request) {
        SysPermissionConfigEntryResp result = sysPermissionConfigService.createPermissionConfig(request);
        return ApiResponse.success("创建成功", result);
    }

    /**
     * 获取权限配置详情
     */
    @GetMapping("/{permissionId}")
    public ApiResponse<SysPermissionConfigDetailResp> getPermissionConfig(@PathVariable Long permissionId) {
        SysPermissionConfigDetailResp result = sysPermissionConfigService.getPermissionConfigById(permissionId);
        return ApiResponse.success(result);
    }

    /**
     * 查询权限配置
     */
    @PostMapping("/query")
    public ApiResponse<List<SysPermissionConfigEntryResp>> queryPermissionConfigs(@RequestBody QuerySysPermissionConfigReq request) {
        List<SysPermissionConfigEntryResp> result = sysPermissionConfigService.queryPermissionConfigs(request);
        return ApiResponse.success(result);
    }

    /**
     * 更新权限配置
     */
    @PutMapping("/{permissionId}")
    public ApiResponse<SysPermissionConfigEntryResp> updatePermissionConfig(@RequestBody UpdateSysPermissionConfigReq request) {
        SysPermissionConfigEntryResp result = sysPermissionConfigService.updatePermissionConfig(request);
        return ApiResponse.success("更新成功", result);
    }

    /**
     * 删除权限配置
     */
    @DeleteMapping("/{permissionId}")
    public ApiResponse<String> deletePermissionConfig(@PathVariable Long permissionId) {
        sysPermissionConfigService.deletePermissionConfig(permissionId);
        return ApiResponse.success("删除成功",null);
    }

    /**
     * 批量删除权限配置
     */
    @PostMapping("/batch-delete")
    public ApiResponse<String> batchDeletePermissionConfigs(@RequestBody List<Long> permissionIds) {
        sysPermissionConfigService.batchDeletePermissionConfigs(permissionIds);
        return ApiResponse.success("批量删除成功",null);
    }

    /**
     * 检查权限
     */
    @GetMapping("/check")
    public ApiResponse<Boolean> checkPermission(
            @RequestParam Long tenantId,
            @RequestParam String tableName,
            @RequestParam String fieldName,
            @RequestParam Integer operation) {
        boolean result = sysPermissionConfigService.hasPermission(tenantId, tableName, fieldName, operation);
        return ApiResponse.success(result);
    }
}
