package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysDataPermissionTargetApi;
import com.jdec.platform.config.api.SysDataPermissionTypeApi;
import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionTargetReq;
import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionTypeReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionTargetSaveReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionTypeSaveReq;
import com.jdec.platform.config.api.dto.response.SysDataPermissionTargetResp;
import com.jdec.platform.config.api.dto.response.SysDataPermissionTypeOptionResp;
import com.jdec.platform.config.api.dto.response.SysDataPermissionTypeResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 数据权限管理 REST 控制器 */
@RestController
@RequestMapping("/api/config/sys/data-permission")
@RequiredArgsConstructor
@Tag(name = "数据权限管理", description = "数据权限类型与目标的增删改查接口")
public class SysDataPermissionController {

    private final SysDataPermissionTypeApi sysDataPermissionTypeApi;
    private final SysDataPermissionTargetApi sysDataPermissionTargetApi;

    // ==================== 数据权限类型 ====================

    @PostMapping("/type/save")
    @Operation(summary = "新增/编辑数据权限类型", description = "id为空则新增，不为空则修改")
    public ApiResponse<SysRightResp> saveType(
            @Valid @RequestBody SysDataPermissionTypeSaveReq request) {
        return ApiResponse.success(sysDataPermissionTypeApi.saveDataPermissionType(request));
    }

    @DeleteMapping("/type/{id}")
    @Operation(
            summary = "删除数据权限类型",
            description = "force=false 时若有角色授权引用则返回提示；force=true 时级联删除角色关联及数据权限目标")
    public ApiResponse<Void> deleteType(
            @Parameter(description = "数据权限类型ID", example = "1") @PathVariable Long id,
            @Parameter(description = "是否强制删除", example = "false")
                    @RequestParam(defaultValue = "false")
                    boolean force) {
        sysDataPermissionTypeApi.deleteDataPermissionType(id, force);
        return ApiResponse.success();
    }

    @GetMapping("/type/options")
    @Operation(summary = "查询数据权限类型下拉列表", description = "查询所有启用的数据权限类型")
    public ApiResponse<List<SysDataPermissionTypeOptionResp>> listTypeOptions() {
        return ApiResponse.success(sysDataPermissionTypeApi.listDataPermissionTypeOptions());
    }

    @GetMapping("/type/list")
    @Operation(summary = "查询数据权限类型列表", description = "支持数据权限类型名称、编码、状态过滤")
    public ApiResponse<List<SysDataPermissionTypeResp>> listType(
            QuerySysDataPermissionTypeReq request) {
        return ApiResponse.success(sysDataPermissionTypeApi.listDataPermissionType(request));
    }

    @GetMapping("/type/{id}")
    @Operation(summary = "查询数据权限类型详情", description = "查询指定数据权限类型详情")
    public ApiResponse<SysDataPermissionTypeResp> getType(
            @Parameter(description = "数据权限类型ID", example = "1") @PathVariable Long id) {
        return ApiResponse.success(sysDataPermissionTypeApi.getDataPermissionType(id));
    }

    // ==================== 数据权限目标 ====================

    @PostMapping("/target/save")
    @Operation(summary = "新增/编辑数据权限目标", description = "id为空则新增，不为空则修改")
    public ApiResponse<Void> saveTarget(
            @Valid @RequestBody SysDataPermissionTargetSaveReq request) {
        sysDataPermissionTargetApi.saveDataPermissionTarget(request);
        return ApiResponse.success();
    }

    @DeleteMapping("/target/{id}")
    @Operation(summary = "删除数据权限目标", description = "删除指定数据权限目标")
    public ApiResponse<Void> deleteTarget(
            @Parameter(description = "数据权限目标ID", example = "1") @PathVariable Long id) {
        sysDataPermissionTargetApi.deleteDataPermissionTarget(id);
        return ApiResponse.success();
    }

    @PostMapping("/target/batch")
    @Operation(summary = "批量保存数据权限目标", description = "先删除原有关联再批量新增")
    public ApiResponse<Void> batchSaveTarget(
            @Parameter(description = "关联role_data_permission.id", example = "1") @RequestParam
                    Long roleDataPermissionId,
            @Valid @RequestBody List<SysDataPermissionTargetSaveReq> targets) {
        sysDataPermissionTargetApi.batchSaveDataPermissionTarget(roleDataPermissionId, targets);
        return ApiResponse.success();
    }

    @GetMapping("/target/list")
    @Operation(summary = "查询数据权限目标列表", description = "支持数据权限目标关联ID、目标类型、目标ID过滤")
    public ApiResponse<List<SysDataPermissionTargetResp>> listTarget(
            QuerySysDataPermissionTargetReq request) {
        return ApiResponse.success(sysDataPermissionTargetApi.listDataPermissionTarget(request));
    }

    @GetMapping("/target/{id}")
    @Operation(summary = "查询数据权限目标详情", description = "查询指定数据权限目标详情")
    public ApiResponse<SysDataPermissionTargetResp> getTarget(
            @Parameter(description = "数据权限目标ID", example = "1") @PathVariable Long id) {
        return ApiResponse.success(sysDataPermissionTargetApi.getDataPermissionTarget(id));
    }
}
