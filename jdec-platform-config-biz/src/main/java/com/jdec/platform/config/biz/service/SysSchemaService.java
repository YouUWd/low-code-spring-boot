package com.jdec.platform.config.biz.service;

import com.jdec.platform.config.api.SysSchemaApi;
import com.jdec.platform.config.api.dto.response.TableInfoResp;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import java.sql.Connection;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/** 物理数据表架构元数据服务实现 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysSchemaService implements SysSchemaApi {

    private final DataSourceResolver dataSourceResolver;

    @Override
    public List<TableInfoResp> getAllTables(String projectNo) {
        log.debug("查询数据源 {} 的所有物理表", projectNo);

        String sql =
                """
                SELECT TABLE_NAME, TABLE_COMMENT
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = ?
                  AND TABLE_NAME NOT LIKE 'approval_%'
                  AND TABLE_NAME NOT LIKE 'module_data_%'
                  AND TABLE_NAME NOT LIKE 'data_engine_%'
                  AND TABLE_NAME NOT LIKE 'monitor_%'
                  AND TABLE_NAME NOT LIKE 'sys_%'
                  AND TABLE_NAME NOT IN ('user_menu_header', 'table_change_log', 'personalized_user_column', 'com_personalized_user_column')
                ORDER BY TABLE_NAME
                """;

        try {
            JdbcTemplate jdbcTemplate = dataSourceResolver.getJdbcTemplate(projectNo);
            String tableSchema = jdbcTemplate.execute((Connection conn) -> conn.getCatalog());
            if (!StringUtils.hasText(tableSchema)) {
                tableSchema = projectNo;
            }

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tableSchema);
            return rows.stream()
                    .map(
                            row ->
                                    TableInfoResp.builder()
                                            .tableName((String) row.get("TABLE_NAME"))
                                            .tableComment((String) row.get("TABLE_COMMENT"))
                                            .build())
                    .toList();
        } catch (Exception e) {
            log.error("查询数据源 {} 的表信息失败", projectNo, e);
            throw new RuntimeException("查询表信息失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> getTableComments(String projectNo, Collection<String> tableNames) {
        if (CollectionUtils.isEmpty(tableNames)) {
            return Collections.emptyMap();
        }

        Set<String> cleanNames = new HashSet<>();
        for (String name : tableNames) {
            if (StringUtils.hasText(name)) {
                cleanNames.add(name.trim());
            }
        }
        if (cleanNames.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> commentMap = new HashMap<>();
        try {
            JdbcTemplate jdbcTemplate = dataSourceResolver.getJdbcTemplate(projectNo);
            String tableSchema = jdbcTemplate.execute((Connection conn) -> conn.getCatalog());
            if (!StringUtils.hasText(tableSchema)) {
                tableSchema = projectNo;
            }

            String inSql = String.join(",", Collections.nCopies(cleanNames.size(), "?"));
            String sql =
                    String.format(
                            "SELECT TABLE_NAME, TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME IN (%s)",
                            inSql);

            List<Object> params = new ArrayList<>();
            params.add(tableSchema);
            params.addAll(cleanNames);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
            for (Map<String, Object> row : rows) {
                String tableName = (String) row.get("TABLE_NAME");
                String comment = (String) row.get("TABLE_COMMENT");
                if (tableName != null) {
                    commentMap.put(
                            tableName.toLowerCase(),
                            StringUtils.hasText(comment) ? comment.trim() : tableName);
                }
            }
        } catch (Exception e) {
            log.warn("批量获取数据源 [{}] 下物理表注释失败", projectNo, e);
        }

        return commentMap;
    }
}
