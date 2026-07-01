package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysDataPermissionApi;
import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.SysDataPermissionResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 数据权限管理 REST 控制器 */
@RestController
@RequestMapping("/api/config/sys/data-permission")
@RequiredArgsConstructor
@Tag(name = "数据权限管理", description = "数据权限管理相关的接口")
public class SysDataPermissionController {

    private final SysDataPermissionApi sysDataPermissionApi;

    @PostMapping("/save")
    @Operation(summary = "新增/编辑数据权限节点", description = "id为空则新增，不为空则修改")
    public ApiResponse<SysRightResp> save(@Valid @RequestBody SysDataPermissionSaveReq request) {
        return ApiResponse.success(sysDataPermissionApi.saveDataPermission(request));
    }

    @GetMapping("/list")
    @Operation(summary = "查询数据权限节点列表", description = "查询当前主体和项目编码下的数据权限节点，并调用业务系统接口查询关联的业务数据")
    public ApiResponse<List<SysDataPermissionResp>> list(QuerySysDataPermissionReq request) {
        return ApiResponse.success(sysDataPermissionApi.listDataPermission(request));
    }
}
