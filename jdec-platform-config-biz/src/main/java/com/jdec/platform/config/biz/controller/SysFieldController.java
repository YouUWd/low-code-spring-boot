package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysFieldApi;
import com.jdec.platform.config.api.dto.request.SysFieldSaveReq;
import com.jdec.platform.config.api.dto.response.ColumnInfoResp;
import com.jdec.platform.config.api.dto.response.SysFieldResp;
import com.jdec.platform.config.api.dto.response.TableInfoResp;
import com.jdec.platform.shared.context.AppContext;
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
 *
 * <p>项目上下文通过 HTTP 请求头 X-Project-No 指定，由 AppContextInterceptor 自动绑定。
 */
@Slf4j
@Tag(name = "字段配置与元数据", description = "提供数据库表、列元数据查询及字段显示配置编辑接口")
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class SysFieldController {

    private final SysFieldApi sysFieldApi;

    @Operation(summary = "获取所有表信息", description = "获取当前项目数据源中所有表的基本信息（表名、注释）")
    @GetMapping("/tables")
    public ApiResponse<List<TableInfoResp>> getAllTables() {
        String projectNo = AppContext.getProjectNo();
        log.info("查询项目 {} 的所有表", projectNo);
        return ApiResponse.success(sysFieldApi.getAllTables(projectNo));
    }

    @Operation(summary = "获取所有表及其列信息", description = "获取当前项目数据源中所有表及其列详细信息（自动合并已配置的显示名称）")
    @GetMapping("/tables-with-columns")
    public ApiResponse<List<TableInfoResp>> getAllTablesWithColumns() {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("查询项目 {} 主体 {} 的所有表及其列信息", projectNo, subjectId);
        return ApiResponse.success(sysFieldApi.getAllTablesWithColumns(projectNo, subjectId));
    }

    @Operation(summary = "获取表的列信息", description = "获取指定表的所有列信息，并自动合并已配置的显示名称和关联关系")
    @GetMapping("/tables/{tableName}/columns")
    public ApiResponse<List<ColumnInfoResp>> getTableColumns(
            @Parameter(description = "表名", example = "sys_module", required = true) @PathVariable
                    String tableName) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("查询项目 {} 主体 {} 的表 {} 的列信息", projectNo, subjectId, tableName);
        return ApiResponse.success(sysFieldApi.getTableColumns(projectNo, subjectId, tableName));
    }

    @Operation(summary = "查询字段配置列表", description = "根据租户ID和表名查询已保存的字段显示配置")
    @GetMapping("/tables/{tableName}/columns/settings")
    public ApiResponse<List<SysFieldResp>> querySysFields(
            @Parameter(description = "表名", example = "sys_module", required = true) @PathVariable
                    String tableName,
            @Parameter(description = "列名（可选）", example = "module_name")
                    @RequestParam(required = false)
                    String columnName) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("查询项目 {} 主体 {} 的表 {} 的字段配置列表", projectNo, subjectId, tableName);
        return ApiResponse.success(
                sysFieldApi.querySysFields(projectNo, subjectId, tableName, columnName));
    }

    @Operation(summary = "查询简版字段配置列表", description = "根据租户ID和表名查询已保存的字段显示配置（不调用词云 Api）")
    @GetMapping("/tables/{tableName}/columns/simple-settings")
    public ApiResponse<List<SysFieldResp>> querySimpleSysFields(
            @Parameter(description = "表名", example = "sys_module", required = true) @PathVariable
                    String tableName,
            @Parameter(description = "列名（可选）", example = "module_name")
                    @RequestParam(required = false)
                    String columnName) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("查询项目 {} 主体 {} 的表 {} 的简版字段配置列表", projectNo, subjectId, tableName);
        return ApiResponse.success(
                sysFieldApi.querySimpleSysFields(projectNo, subjectId, tableName, columnName));
    }

    @Operation(summary = "保存字段配置", description = "保存或更新单个字段的显示名称、关联关系等配置")
    @PostMapping("/tables/{tableName}/columns/settings")
    public ApiResponse<SysFieldResp> saveSysField(
            @Parameter(description = "表名", example = "sys_module", required = true) @PathVariable
                    String tableName,
            @RequestBody SysFieldSaveReq req) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info(
                "保存项目 {} 主体 {} 的表 {} 的字段 {} 配置",
                projectNo,
                subjectId,
                tableName,
                req.getColumnName());
        return ApiResponse.success(sysFieldApi.saveSysField(projectNo, subjectId, tableName, req));
    }

    @Operation(summary = "查询数据权限字段列表", description = "查询被设置为生成数据权限节点的字段记录列表（dataRightFlag=1）")
    @GetMapping("/data-permission-fields")
    public ApiResponse<List<SysFieldResp>> getDataPermissionFields() {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        log.info("查询项目 {} 主体 {} 的数据权限字段列表", projectNo, subjectId);
        return ApiResponse.success(sysFieldApi.getDataPermissionFields(projectNo, subjectId));
    }
}
