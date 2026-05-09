package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.dto.response.ColumnInfoResp;
import com.jdec.platform.config.api.dto.response.DatabaseSchemaResp;
import com.jdec.platform.config.api.dto.response.TableInfoResp;
import com.jdec.platform.config.biz.service.DatabaseMetadataService;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据库元数据查询控制器
 *
 * <p>提供数据库表、列等元数据信息的查询接口。
 *
 * <p>数据源通过 HTTP 请求头 X-Tenant-Id 指定，由 TenantInterceptor 自动设置。
 *
 * <p>使用示例：
 *
 * <pre>
 * # 查询所有表
 * curl -H "X-Tenant-Id: config_center" http://localhost:8080/api/metadata/tables
 *
 * # 查询表的列
 * curl -H "X-Tenant-Id: config_center" http://localhost:8080/api/metadata/tables/sys_module/columns
 *
 * # 查询完整的数据库 Schema
 * curl -H "X-Tenant-Id: config_center" http://localhost:8080/api/metadata/schema
 * </pre>
 */
@Slf4j
@Tag(name = "数据库元数据", description = "提供数据库表、列等元数据信息的查询接口")
@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
public class MetadataController {

    private final DatabaseMetadataService metadataService;

    /**
     * 获取数据库中所有表的基本信息
     *
     * <p>数据源通过 X-Tenant-Id 请求头指定。
     *
     * @param tenantId 租户/数据源 ID（通过 X-Tenant-Id 请求头指定）
     * @return 表信息列表
     */
    @Operation(summary = "获取所有表", description = "获取数据库中所有表的基本信息（表名、表描述等）")
    @GetMapping("/tables")
    public ApiResponse<List<TableInfoResp>> getAllTables(
            @Parameter(description = "租户/数据源 ID", example = "config_center", required = true)
                    @RequestHeader("X-Tenant-Id")
                    String tenantId) {
        log.info("查询数据源 {} 的所有表", tenantId);
        List<TableInfoResp> tables = metadataService.getAllTables();

        return ApiResponse.success(tables);
    }

    /**
     * 获取表的所有列信息
     *
     * <p>数据源通过 X-Tenant-Id 请求头指定。
     *
     * @param tableName 表名
     * @param tenantId 租户/数据源 ID（通过 X-Tenant-Id 请求头指定）
     * @return 列信息列表
     */
    @Operation(summary = "获取表的列信息", description = "获取指定表的所有列信息（列名、数据类型、是否可空等）")
    @GetMapping("/tables/{tableName}/columns")
    public ApiResponse<List<ColumnInfoResp>> getTableColumns(
            @Parameter(description = "表名", example = "sys_module", required = true) @PathVariable
                    String tableName,
            @Parameter(description = "租户/数据源 ID", example = "config_center", required = true)
                    @RequestHeader("X-Tenant-Id")
                    String tenantId) {
        log.info("查询数据源 {} 的表 {} 的列信息", tenantId, tableName);
        List<ColumnInfoResp> columns = metadataService.getTableColumns(tableName);

        return ApiResponse.success(columns);
    }

    /**
     * 获取数据库的完整 Schema 信息（包括所有表、列）
     *
     * <p>数据源通过 X-Tenant-Id 请求头指定。
     *
     * <p>注意：此操作会加载整个数据库的元数据，对于大型数据库可能耗时较长。
     *
     * @param tenantId 租户/数据源 ID（通过 X-Tenant-Id 请求头指定）
     * @return 完整的数据库 Schema 信息
     */
    @Operation(
            summary = "获取完整数据库Schema",
            description = "获取数据库的完整Schema信息，包括所有表及其列的详细信息。注意：此操作可能耗时较长")
    @GetMapping("/schema")
    public ApiResponse<DatabaseSchemaResp> getDatabaseSchema(
            @Parameter(description = "租户/数据源 ID", example = "config_center", required = true)
                    @RequestHeader("X-Tenant-Id")
                    String tenantId) {
        log.info("查询数据源 {} 的完整 Schema", tenantId);

        // 使用优化的单条 SQL 查询获取所有表和列信息
        List<TableInfoResp> tables = metadataService.getAllTablesWithColumns();

        DatabaseSchemaResp schema = DatabaseSchemaResp.builder().tables(tables).build();

        log.info("成功查询数据源 {} 的 Schema，共 {} 张表", tenantId, tables.size());

        return ApiResponse.success(schema);
    }
}
