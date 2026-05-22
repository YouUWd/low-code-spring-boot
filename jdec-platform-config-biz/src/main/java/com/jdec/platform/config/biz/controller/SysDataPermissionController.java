package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysDataPermissionApi;
import com.jdec.platform.config.api.SysDataPermissionTypeApi;
import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionTypeReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionTypeSaveReq;
import com.jdec.platform.config.api.dto.response.SysDataPermissionResp;
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
@RequestMapping("/api/config/sys/data-permission/type")
@RequiredArgsConstructor
@Tag(name = "数据权限管理", description = "数据权限类型与权限实例的增删改查接口")
public class SysDataPermissionController {

    private final SysDataPermissionTypeApi sysDataPermissionTypeApi;
    private final SysDataPermissionApi sysDataPermissionApi;

    // ==================== 数据权限类型 ====================

    @PostMapping("/save")
    @Operation(summary = "新增/编辑数据权限类型", description = "id为空则新增，不为空则修改")
    public ApiResponse<SysRightResp> saveType(
            @Valid @RequestBody SysDataPermissionTypeSaveReq request) {
        return ApiResponse.success(sysDataPermissionTypeApi.saveDataPermissionType(request));
    }

    @DeleteMapping("/{id}")
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

    @GetMapping("/options")
    @Operation(summary = "查询数据权限类型下拉列表", description = "查询所有启用的数据权限类型")
    public ApiResponse<List<SysDataPermissionTypeOptionResp>> listTypeOptions() {
        return ApiResponse.success(sysDataPermissionTypeApi.listDataPermissionTypeOptions());
    }

    @GetMapping("/list")
    @Operation(summary = "查询数据权限类型列表", description = "支持数据权限类型名称、编码、状态过滤")
    public ApiResponse<List<SysDataPermissionTypeResp>> listType(
            QuerySysDataPermissionTypeReq request) {
        return ApiResponse.success(sysDataPermissionTypeApi.listDataPermissionType(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询数据权限类型详情", description = "查询指定数据权限类型详情")
    public ApiResponse<SysDataPermissionTypeResp> getType(
            @Parameter(description = "数据权限类型ID", example = "1") @PathVariable Long id) {
        return ApiResponse.success(sysDataPermissionTypeApi.getDataPermissionType(id));
    }

    // ==================== 数据权限实例 ====================

    @GetMapping("/categories")
    @Operation(summary = "查询权限分类列表", description = "查询所有权限分类（type_code/type_name去重）")
    public ApiResponse<List<SysDataPermissionResp>> listCategories() {
        return ApiResponse.success(sysDataPermissionApi.listDataPermissionCategories());
    }
}
