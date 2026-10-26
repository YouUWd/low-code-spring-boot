package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysInteractionPermissionApi;
import com.jdec.platform.config.api.dto.request.QuerySysInteractionPermissionReq;
import com.jdec.platform.config.api.dto.request.SysInteractionPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionDetailResp;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionListResp;
import com.jdec.platform.config.api.dto.response.SysModuleInteractionPermissionResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
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
    public ApiResponse<SysRightResp> save(
            @Valid @RequestBody SysInteractionPermissionSaveReq request) {
        return ApiResponse.success(sysInteractionPermissionApi.saveInteractionPermission(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除交互权限", description = "force=false 时若有角色授权引用则返回提示；force=true 时级联删除角色关联")
    public ApiResponse<Void> delete(
            @Parameter(description = "交互权限ID", example = "1") @PathVariable Long id,
            @Parameter(description = "是否强制删除", example = "false")
                    @RequestParam(defaultValue = "false")
                    boolean force) {
        sysInteractionPermissionApi.deleteInteractionPermission(id, force);
        return ApiResponse.success();
    }

    @GetMapping("/list")
    @Operation(summary = "查询交互权限列表", description = "支持交互权限名称、编码、状态、交互权限类型过滤")
    public ApiResponse<List<SysInteractionPermissionListResp>> listInteractionPermission(
            QuerySysInteractionPermissionReq request) {
        return ApiResponse.success(sysInteractionPermissionApi.listInteractionPermission(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询交互权限详情")
    public ApiResponse<SysInteractionPermissionDetailResp> getInteractionPermission(
            @Parameter(description = "交互权限ID", example = "1") @PathVariable Long id) {
        return ApiResponse.success(sysInteractionPermissionApi.getInteractionPermission(id));
    }

    @GetMapping("/module-tree")
    @Operation(summary = "查询模块交互权限树", description = "返回模块树，每个模块节点直接携带对应的交互权限列表")
    public ApiResponse<List<SysModuleInteractionPermissionResp>>
            listModuleInteractionPermissionTree(
                    @Parameter(description = "模块分类.1:业务模块 2:系统模块（可选）", example = "1")
                            @RequestParam(required = false)
                            Integer category) {
        return ApiResponse.success(
                sysInteractionPermissionApi.listModuleInteractionPermissionTree(category));
    }
}
