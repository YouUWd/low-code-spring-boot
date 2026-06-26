package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysDataPermissionApi;
import com.jdec.platform.config.api.SysDataPermissionMappingApi;
import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.SysDataPermissionMappingResp;
import com.jdec.platform.config.api.dto.response.SysDataPermissionOptionResp;
import com.jdec.platform.config.api.dto.response.SysDataPermissionResp;
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
@RequestMapping("/api/config/sys/data-permission/type")
@RequiredArgsConstructor
@Tag(name = "数据权限管理", description = "数据权限类型与权限实例的增删改查接口")
public class SysDataPermissionController {

    private final SysDataPermissionApi sysDataPermissionApi;
    private final SysDataPermissionMappingApi sysDataPermissionMappingApi;

    // ==================== 数据权限节点 ====================

    @PostMapping("/save")
    @Operation(summary = "新增/编辑数据权限节点", description = "id为空则新增，不为空则修改")
    public ApiResponse<SysRightResp> save(@Valid @RequestBody SysDataPermissionSaveReq request) {
        return ApiResponse.success(sysDataPermissionApi.saveDataPermission(request));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "删除数据权限类型",
            description = "force=false 时若有角色授权引用则返回提示；force=true 时级联删除角色关联及数据权限目标")
    public ApiResponse<Void> delete(
            @Parameter(description = "数据权限类型ID", example = "1") @PathVariable Long id,
            @Parameter(description = "是否强制删除", example = "false")
                    @RequestParam(defaultValue = "false")
                    boolean force) {
        sysDataPermissionApi.deleteDataPermission(id, force);
        return ApiResponse.success();
    }

    @GetMapping("/options")
    @Operation(summary = "查询数据权限节点下拉列表", description = "查询所有启用的数据权限节点")
    public ApiResponse<List<SysDataPermissionOptionResp>> listOptions() {
        return ApiResponse.success(sysDataPermissionApi.listDataPermissionOptions());
    }

    @GetMapping("/list")
    @Operation(summary = "查询数据权限节点列表", description = "支持数据权限节点名称、编码、状态过滤")
    public ApiResponse<List<SysDataPermissionResp>> list(QuerySysDataPermissionReq request) {
        return ApiResponse.success(sysDataPermissionApi.listDataPermission(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询数据权限节点详情", description = "查询指定数据权限节点详情")
    public ApiResponse<SysDataPermissionResp> get(
            @Parameter(description = "数据权限节点ID", example = "1") @PathVariable Long id) {
        return ApiResponse.success(sysDataPermissionApi.getDataPermission(id));
    }

    // ==================== 数据权限实例 ====================

    @GetMapping("/categories")
    @Operation(summary = "查询权限分类列表", description = "查询所有权限分类")
    public ApiResponse<List<SysDataPermissionMappingResp>> listCategories() {
        return ApiResponse.success(sysDataPermissionMappingApi.listDataPermissionCategories());
    }
}
