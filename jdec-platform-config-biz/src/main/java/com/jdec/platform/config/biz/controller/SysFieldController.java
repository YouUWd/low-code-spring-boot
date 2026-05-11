package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysFieldApi;
import com.jdec.platform.config.api.dto.request.SysFieldSaveReq;
import com.jdec.platform.config.api.dto.response.ColumnInfoResp;
import com.jdec.platform.config.api.dto.response.DatabaseSchemaResp;
import com.jdec.platform.config.api.dto.response.SysFieldResp;
import com.jdec.platform.config.api.dto.response.TableInfoResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 系统字段配置与元数据控制器
 *
 * <p>集成了原 MetadataController 的功能，并增加了字段配置的编辑功能。
 */
@Slf4j
@Tag(name = "字段配置与元数据", description = "提供数据库表、列元数据查询及字段显示配置编辑接口")
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class SysFieldController {

    private final SysFieldApi sysFieldApi;

    @Operation(summary = "获取所有表信息", description = "获取当前数据源中所有表的基本信息（表名、注释）")
    @GetMapping("/tables")
    public ApiResponse<List<TableInfoResp>> getAllTables(
            @Parameter(description = "租户/数据源 ID", example = "config_center", required = true)
                    @RequestHeader("X-Tenant-Id")
                    String tenantId) {
        log.info("查询数据源 {} 的所有表", tenantId);
        return ApiResponse.success(sysFieldApi.getAllTables(tenantId));
    }

    @Operation(summary = "获取表的列信息", description = "获取指定表的所有列信息，并自动合并已配置的显示名称和关联关系")
    @GetMapping("/tables/{tableName}/columns")
    public ApiResponse<List<ColumnInfoResp>> getTableColumns(
            @Parameter(description = "表名", example = "sys_module", required = true) @PathVariable
                    String tableName,
            @Parameter(description = "租户/数据源 ID", example = "config_center", required = true)
                    @RequestHeader("X-Tenant-Id")
                    String tenantId) {
        log.info("查询数据源 {} 的表 {} 的列信息", tenantId, tableName);
        return ApiResponse.success(sysFieldApi.getTableColumns(tenantId, tableName));
    }


    @Operation(summary = "查询字段配置列表", description = "根据租户ID和表名查询已保存的字段显示配置")
    @GetMapping("/tables/{tableName}/columns/settings")
    public ApiResponse<List<SysFieldResp>> querySysFields(
            @Parameter(description = "表名", example = "sys_module", required = true) @PathVariable
                    String tableName,
            @Parameter(description = "租户/数据源 ID", example = "config_center", required = true)
                    @RequestHeader("X-Tenant-Id")
                    String tenantId) {
        log.info("查询数据源 {} 的表 {} 的字段配置列表", tenantId, tableName);
        return ApiResponse.success(sysFieldApi.querySysFields(tenantId, tableName));
    }

    @Operation(summary = "保存字段配置", description = "保存或更新单个字段的显示名称、关联关系等配置")
    @PostMapping("/tables/{tableName}/columns/settings")
    public ApiResponse<Void> saveSysField(
            @Parameter(description = "表名", example = "sys_module", required = true) @PathVariable
                    String tableName,
            @Parameter(description = "租户/数据源 ID", example = "config_center", required = true)
                    @RequestHeader("X-Tenant-Id")
                    String tenantId,
            @RequestBody SysFieldSaveReq req) {
        log.info("保存数据源 {} 的表 {} 的字段 {} 配置", tenantId, tableName, req.getColumnName());
        sysFieldApi.saveSysField(tenantId, tableName, req);
        return ApiResponse.success();
    }
}
