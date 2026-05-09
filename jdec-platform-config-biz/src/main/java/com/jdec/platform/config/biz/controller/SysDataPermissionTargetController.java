package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysDataPermissionTargetApi;
import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionTargetReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionTargetSaveReq;
import com.jdec.platform.config.api.dto.response.SysDataPermissionTargetResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 数据权限目标管理 REST 控制器 */
@RestController
@RequestMapping("/api/config/sys/data-permission-target")
@RequiredArgsConstructor
@Tag(name = "数据权限目标管理", description = "数据权限目标的增删改查接口")
public class SysDataPermissionTargetController {

    private final SysDataPermissionTargetApi sysDataPermissionTargetApi;

    @PostMapping("/save")
    @Operation(summary = "新增/编辑数据权限目标", description = "id为空则新增，不为空则修改")
    public ApiResponse<Void> save(@Valid @RequestBody SysDataPermissionTargetSaveReq request) {
        sysDataPermissionTargetApi.saveDataPermissionTarget(request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除数据权限目标", description = "删除指定数据权限目标")
    public ApiResponse<Void> delete(
            @Parameter(description = "数据权限目标ID", example = "1") @PathVariable Long id) {
        sysDataPermissionTargetApi.deleteDataPermissionTarget(id);
        return ApiResponse.success();
    }

    @PostMapping("/batch")
    @Operation(summary = "批量保存数据权限目标", description = "先删除原有关联再批量新增")
    public ApiResponse<Void> batchSave(
            @Parameter(description = "关联role_data_permission.id", example = "1") @RequestParam
                    Long roleDataPermissionId,
            @Valid @RequestBody List<SysDataPermissionTargetSaveReq> targets) {
        sysDataPermissionTargetApi.batchSaveDataPermissionTarget(roleDataPermissionId, targets);
        return ApiResponse.success();
    }

    @GetMapping("/list")
    @Operation(summary = "查询数据权限目标列表", description = "支持数据权限目标关联ID、目标类型、目标ID过滤")
    public ApiResponse<List<SysDataPermissionTargetResp>> listDataPermissionTarget(
            QuerySysDataPermissionTargetReq request) {
        return ApiResponse.success(sysDataPermissionTargetApi.listDataPermissionTarget(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询数据权限目标详情", description = "查询指定数据权限目标详情")
    public ApiResponse<SysDataPermissionTargetResp> getDataPermissionTarget(
            @Parameter(description = "数据权限目标ID", example = "1") @PathVariable Long id) {
        return ApiResponse.success(sysDataPermissionTargetApi.getDataPermissionTarget(id));
    }
}
