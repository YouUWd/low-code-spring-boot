package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysInteractionPermissionApi;
import com.jdec.platform.config.api.dto.request.QuerySysInteractionPermissionReq;
import com.jdec.platform.config.api.dto.request.SysInteractionPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionDetailResp;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionListResp;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionOptionResp;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 交互权限管理 REST 控制器 */
@RestController
@RequestMapping("/api/config/sys/interaction-permission")
@RequiredArgsConstructor
@Tag(name = "交互权限管理", description = "交互权限的增删改查接口")
public class SysInteractionPermissionController {

    private final SysInteractionPermissionApi sysInteractionPermissionApi;

    @PostMapping("/save")
    @Operation(summary = "新增/编辑交互权限", description = "id为空则新增，不为空则修改")
    public ApiResponse<Void> save(@Valid @RequestBody SysInteractionPermissionSaveReq request) {
        sysInteractionPermissionApi.saveInteractionPermission(request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除交互权限", description = "删除指定交互权限及其所有子节点")
    public ApiResponse<Void> delete(
            @Parameter(description = "交互权限ID", example = "1") @PathVariable Long id) {
        sysInteractionPermissionApi.deleteInteractionPermission(id);
        return ApiResponse.success();
    }

    @GetMapping("/parent/options")
    @Operation(summary = "查询父级交互权限下拉列表", description = "查询所有顶级交互权限")
    public ApiResponse<List<SysInteractionPermissionOptionResp>> listParentOptions() {
        return ApiResponse.success(
                sysInteractionPermissionApi.listParentInteractionPermissionOptions());
    }

    @GetMapping("/list")
    @Operation(summary = "查询交互权限列表", description = "支持交互权限名称、编码、状态过滤")
    public ApiResponse<List<SysInteractionPermissionListResp>> listInteractionPermission(
            QuerySysInteractionPermissionReq request) {
        return ApiResponse.success(sysInteractionPermissionApi.listInteractionPermission(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询交互权限详情", description = "查询指定交互权限详情")
    public ApiResponse<SysInteractionPermissionDetailResp> getInteractionPermission(
            @Parameter(description = "交互权限ID", example = "1") @PathVariable Long id) {
        return ApiResponse.success(sysInteractionPermissionApi.getInteractionPermission(id));
    }

    @GetMapping("/tree")
    @Operation(summary = "查询交互权限树形结构", description = "查询所有交互权限并构建树形结构")
    public ApiResponse<List<SysInteractionPermissionResp>> listInteractionPermissionTree() {
        return ApiResponse.success(sysInteractionPermissionApi.listInteractionPermissionTree());
    }
}
