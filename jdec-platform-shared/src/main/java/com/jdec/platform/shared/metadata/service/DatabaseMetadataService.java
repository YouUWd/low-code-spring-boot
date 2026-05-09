package com.jdec.platform.shared.metadata.service;

import com.jdec.platform.shared.datasource.DataSourceContextHolder;
import com.jdec.platform.shared.metadata.dto.ColumnInfo;
import com.jdec.platform.shared.metadata.dto.TableInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 数据库元数据查询服务
 *
 * <p>用于查询数据库的表、列等元数据信息。
 *
 * <p>数据源通过 X-Tenant-Id 请求头自动设置，由 TenantInterceptor 处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseMetadataService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 获取当前数据源名称
     *
     * @return 当前数据源名称
     * @throws IllegalStateException 如果未设置数据源
     */
    private String getCurrentDatabase() {
        return DataSourceContextHolder.getRequiredDataSource();
    }

    /**
     * 获取数据库中所有表的基本信息
     *
     * <p>数据源从 DataSourceContextHolder 获取（由 TenantInterceptor 设置）。
     *
     * @return 表信息列表
     */
    public List<TableInfo> getAllTables() {
        String database = getCurrentDatabase();
        log.debug("查询数据源 {} 的所有表", database);

        String sql =
                """
            SELECT TABLE_NAME, TABLE_COMMENT
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = ?
            ORDER BY TABLE_NAME
            """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, database);
            return rows.stream()
                    .map(
                            row ->
                                    TableInfo.builder()
                                            .tableName((String) row.get("TABLE_NAME"))
                                            .tableComment((String) row.get("TABLE_COMMENT"))
                                            .build())
                    .toList();
        } catch (Exception e) {
            log.error("查询数据源 {} 的表信息失败", database, e);
            throw new RuntimeException("查询表信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取数据库中所有表及其列的完整信息（优化版本，一次查询）
     *
     * <p>使用单条 SQL 查询避免 N+1 问题，性能更优。
     *
     * @return 表信息列表（包含列信息）
     */
    public List<TableInfo> getAllTablesWithColumns() {
        String database = getCurrentDatabase();
        log.debug("查询数据源 {} 的所有表及列信息（优化版本）", database);

        String sql =
                """
            SELECT
                t.TABLE_NAME,
                t.TABLE_COMMENT,
                c.COLUMN_NAME,
                c.COLUMN_TYPE,
                c.COLUMN_COMMENT
            FROM INFORMATION_SCHEMA.TABLES t
            LEFT JOIN INFORMATION_SCHEMA.COLUMNS c
                ON t.TABLE_SCHEMA = c.TABLE_SCHEMA
                AND t.TABLE_NAME = c.TABLE_NAME
            WHERE t.TABLE_SCHEMA = ?
            ORDER BY t.TABLE_NAME, c.ORDINAL_POSITION
            """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, database);

            // 使用 LinkedHashMap 保持表的顺序
            LinkedHashMap<String, TableInfo> tableMap = new LinkedHashMap<>();

            for (Map<String, Object> row : rows) {
                String tableName = (String) row.get("TABLE_NAME");

                // 如果表不存在，创建新的表信息
                tableMap.computeIfAbsent(
                        tableName,
                        k ->
                                TableInfo.builder()
                                        .tableName(tableName)
                                        .tableComment((String) row.get("TABLE_COMMENT"))
                                        .columns(new ArrayList<>())
                                        .build());

                // 如果有列信息，添加到表中
                String columnName = (String) row.get("COLUMN_NAME");
                if (columnName != null) {
                    ColumnInfo column =
                            ColumnInfo.builder()
                                    .columnName(columnName)
                                    .columnType((String) row.get("COLUMN_TYPE"))
                                    .columnComment((String) row.get("COLUMN_COMMENT"))
                                    .build();

                    tableMap.get(tableName).getColumns().add(column);
                }
            }

            return new ArrayList<>(tableMap.values());
        } catch (Exception e) {
            log.error("查询数据源 {} 的表和列信息失败", database, e);
            throw new RuntimeException("查询表和列信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取表的所有列信息
     *
     * <p>数据源从 DataSourceContextHolder 获取（由 TenantInterceptor 设置）。
     *
     * @param tableName 表名
     * @return 列信息列表
     */
    public List<ColumnInfo> getTableColumns(String tableName) {
        String database = getCurrentDatabase();
        log.debug("查询表 {}.{} 的列信息", database, tableName);

        String sql =
                """
            SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT, IS_NULLABLE, COLUMN_KEY, EXTRA
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
            ORDER BY ORDINAL_POSITION
            """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, database, tableName);
            return rows.stream()
                    .map(
                            row ->
                                    ColumnInfo.builder()
                                            .columnName((String) row.get("COLUMN_NAME"))
                                            .columnType((String) row.get("COLUMN_TYPE"))
                                            .columnComment((String) row.get("COLUMN_COMMENT"))
                                            .build())
                    .toList();
        } catch (Exception e) {
            log.error("查询表 {}.{} 的列信息失败", database, tableName, e);
            throw new RuntimeException("查询列信息失败: " + e.getMessage(), e);
        }
    }
}
