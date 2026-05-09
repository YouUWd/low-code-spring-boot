package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysRightApi;
import com.jdec.platform.config.api.dto.request.QuerySysRightReq;
import com.jdec.platform.config.api.dto.request.SysRightSaveReq;
import com.jdec.platform.config.api.dto.response.SysRightOptionResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限节点管理 REST 控制器
 */
@RestController
@RequestMapping("/api/config/sys/right")
@RequiredArgsConstructor
@Tag(name = "权限节点管理", description = "权限节点的增删改查接口")
public class SysRightController {

    private final SysRightApi sysRightApi;

    @PostMapping("/save")
    @Operation(summary = "新增/编辑权限节点", description = "id为空则新增，不为空则修改")
    public ApiResponse<Void> save(@Valid @RequestBody SysRightSaveReq request) {
        sysRightApi.saveRight(request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除权限节点", description = "删除指定权限节点及其所有子节点")
    public ApiResponse<Void> delete(
            @Parameter(description = "权限节点ID", example = "1")
            @PathVariable Long id) {
        sysRightApi.deleteRight(id);
        return ApiResponse.success();
    }

    @GetMapping("/parent/options")
    @Operation(summary = "查询父级权限节点下拉列表", description = "通过节点类型查询pid为0的权限节点列表")
    public ApiResponse<List<SysRightOptionResp>> listParentOptions(
            @Parameter(description = "节点类型.1:数据权限,2:交互权限,3:其它权限")
            @RequestParam(required = false) Integer nodeType) {
        return ApiResponse.success(sysRightApi.listParentOptions(nodeType));
    }

    @GetMapping("/list")
    @Operation(summary = "查询权限节点列表", description = "支持权限节点名称过滤")
    public ApiResponse<List<SysRightResp>> listRight(QuerySysRightReq request) {
        return ApiResponse.success(sysRightApi.listRight(request));
    }
}
