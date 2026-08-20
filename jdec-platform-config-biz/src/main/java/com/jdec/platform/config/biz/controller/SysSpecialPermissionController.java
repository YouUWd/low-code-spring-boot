package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysSpecialPermissionApi;
import com.jdec.platform.config.api.dto.request.CheckUserSpecialPermissionReq;
import com.jdec.platform.config.api.dto.request.QuerySysSpecialPermissionReq;
import com.jdec.platform.config.api.dto.request.SysSpecialPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.CheckUserSpecialPermissionResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import com.jdec.platform.config.api.dto.response.SysSpecialPermissionOptionResp;
import com.jdec.platform.config.api.dto.response.SysSpecialPermissionResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 特殊权限管理 REST 控制器 */
@RestController
@RequestMapping("/api/config/sys/special-permission")
@RequiredArgsConstructor
@Tag(name = "特殊权限管理", description = "特殊权限的增删改查接口")
public class SysSpecialPermissionController {

    private final SysSpecialPermissionApi sysSpecialPermissionApi;

    @PostMapping("/save")
    @Operation(summary = "新增/编辑特殊权限", description = "id为空则新增，不为空则修改")
    public ApiResponse<SysRightResp> save(@Valid @RequestBody SysSpecialPermissionSaveReq request) {
        return ApiResponse.success(sysSpecialPermissionApi.saveSpecialPermission(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除特殊权限", description = "force=false 时若有角色授权引用则返回提示；force=true 时级联删除角色关联")
    public ApiResponse<Void> delete(
            @Parameter(description = "特殊权限ID", example = "1") @PathVariable Long id,
            @Parameter(description = "是否强制删除", example = "false")
                    @RequestParam(defaultValue = "false")
                    boolean force) {
        sysSpecialPermissionApi.deleteSpecialPermission(id, force);
        return ApiResponse.success();
    }

    @GetMapping("/options")
    @Operation(summary = "查询特殊权限下拉列表", description = "查询所有启用的特殊权限")
    public ApiResponse<List<SysSpecialPermissionOptionResp>> listOptions() {
        return ApiResponse.success(sysSpecialPermissionApi.listSpecialPermissionOptions());
    }

    @GetMapping("/list")
    @Operation(summary = "查询特殊权限列表", description = "支持特殊权限名称、编码、状态过滤")
    public ApiResponse<List<SysSpecialPermissionResp>> listSpecialPermission(
            QuerySysSpecialPermissionReq request) {
        return ApiResponse.success(sysSpecialPermissionApi.listSpecialPermission(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询特殊权限详情", description = "查询指定特殊权限详情")
    public ApiResponse<SysSpecialPermissionResp> getSpecialPermission(
            @Parameter(description = "特殊权限ID", example = "1") @PathVariable Long id) {
        return ApiResponse.success(sysSpecialPermissionApi.getSpecialPermission(id));
    }

    @PostMapping("/check")
    @Operation(summary = "检查用户是否拥有特殊权限", description = "根据用户ID和特殊权限编码，检查用户在当前主体下是否拥有该特殊权限，并验证时效性")
    public ApiResponse<CheckUserSpecialPermissionResp> checkUserSpecialPermission(
            @Valid @RequestBody CheckUserSpecialPermissionReq request) {
        return ApiResponse.success(sysSpecialPermissionApi.checkUserSpecialPermission(request));
    }
}
