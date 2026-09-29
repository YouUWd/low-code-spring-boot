package com.jdec.platform.config.biz.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.SysSearchConfigApi;
import com.jdec.platform.config.api.dto.response.SearchBusinessTypeOptionResp;
import com.jdec.platform.config.api.dto.response.SearchConfigItemResp;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import com.jdec.platform.shared.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 下拉搜索配置服务：读取 search_config.json，按字段类型返回可用搜索配置 */
@Slf4j
@Service
public class SysSearchConfigService implements SysSearchConfigApi {

    private static final String CONFIG_PATH = "search_config.json";

    /** 按数据源库名 + 表名 + 列名查询字段数据类型 */
    private static final String COLUMN_TYPE_SQL =
            """
            SELECT DATA_TYPE
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?
            LIMIT 1
            """;

    private final DataSourceResolver dataSourceResolver;

    /** dataType（小写）→ 搜索配置列表 */
    private final Map<String, List<SearchConfigItemResp>> configMap;

    /** 业务类型下拉选项（按 businessType 去重，保持配置文件顺序） */
    private final List<SearchBusinessTypeOptionResp> businessTypeOptions;

    public SysSearchConfigService(
            ObjectMapper objectMapper, DataSourceResolver dataSourceResolver) {
        this.dataSourceResolver = dataSourceResolver;
        List<SearchConfigEntry> entries = loadEntries(objectMapper);
        this.configMap = buildConfigMap(entries);
        this.businessTypeOptions = buildBusinessTypeOptions(entries);
    }

    @Override
    public List<SearchConfigItemResp> listSearchConfig(String dataIndex) {
        String[] tableAndColumn = parseDataIndex(dataIndex);
        String dataType = queryColumnType(tableAndColumn[0], tableAndColumn[1]);
        String key = normalize(dataType);
        if (key == null) {
            return Collections.emptyList();
        }
        return configMap.getOrDefault(key, Collections.emptyList());
    }

    @Override
    public List<SearchBusinessTypeOptionResp> listBusinessTypeOptions() {
        return businessTypeOptions;
    }

    /** 拆分字段索引「模块ID.表名.字段名」，返回 [表名, 字段名] */
    private String[] parseDataIndex(String dataIndex) {
        if (!StringUtils.hasText(dataIndex)) {
            throw new BusinessException("字段索引不能为空");
        }
        String[] parts = dataIndex.trim().split("\\.");
        if (parts.length < 2 || !StringUtils.hasText(parts[parts.length - 2])) {
            throw new BusinessException("字段索引格式不正确: " + dataIndex);
        }
        String tableName = parts[parts.length - 2].trim();
        String columnName = parts[parts.length - 1].trim();
        if (!StringUtils.hasText(columnName)) {
            throw new BusinessException("字段索引格式不正确: " + dataIndex);
        }
        return new String[] {tableName, columnName};
    }

    /** 按当前项目数据源查询 information_schema，字段不存在时返回 null */
    private String queryColumnType(String tableName, String columnName) {
        String projectNo = AppContext.getProjectNo();
        try {
            JdbcTemplate jdbcTemplate = dataSourceResolver.getJdbcTemplate(projectNo);
            String tableSchema = jdbcTemplate.execute((Connection conn) -> conn.getCatalog());
            if (!StringUtils.hasText(tableSchema)) {
                tableSchema = projectNo;
            }

            List<String> types =
                    jdbcTemplate.queryForList(
                            COLUMN_TYPE_SQL, String.class, tableSchema, tableName, columnName);
            if (types.isEmpty()) {
                log.warn("数据源 {} 中未找到字段 {}.{}", projectNo, tableName, columnName);
                return null;
            }
            return types.get(0);
        } catch (Exception e) {
            log.error("查询字段 {}.{} 的数据类型失败", tableName, columnName, e);
            throw new BusinessException("查询字段数据类型失败: " + e.getMessage());
        }
    }

    /** 读取 search_config.json */
    private List<SearchConfigEntry> loadEntries(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(CONFIG_PATH);
        if (!resource.exists()) {
            throw new BusinessException("搜索配置文件不存在: " + CONFIG_PATH);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<>() {});
        } catch (IOException e) {
            throw new BusinessException("读取搜索配置失败: " + CONFIG_PATH + "，原因: " + e.getMessage());
        }
    }

    /** 建立 dataType（小写）索引 */
    private Map<String, List<SearchConfigItemResp>> buildConfigMap(
            List<SearchConfigEntry> entries) {
        Map<String, List<SearchConfigItemResp>> map = new LinkedHashMap<>();
        for (SearchConfigEntry entry : entries) {
            if (entry.getDataType() != null) {
                map.put(
                        entry.getDataType().toLowerCase(),
                        entry.getSearchConfig() != null
                                ? entry.getSearchConfig()
                                : Collections.emptyList());
            }
        }
        log.info("已加载 {} 条字段搜索配置", map.size());
        return map;
    }

    /** 提取 businessType / businessTypeName，按 businessType 去重 */
    private List<SearchBusinessTypeOptionResp> buildBusinessTypeOptions(
            List<SearchConfigEntry> entries) {
        Map<String, SearchBusinessTypeOptionResp> options = new LinkedHashMap<>();
        for (SearchConfigEntry entry : entries) {
            if (entry.getBusinessType() == null || entry.getBusinessType().isBlank()) {
                continue;
            }
            options.putIfAbsent(
                    entry.getBusinessType(),
                    new SearchBusinessTypeOptionResp(
                            entry.getBusinessType(), entry.getBusinessTypeName()));
        }
        return new ArrayList<>(options.values());
    }

    /** 字段类型归一化：转小写并截掉长度/精度声明，如 VARCHAR(64) → varchar、int unsigned → int */
    private String normalize(String dataType) {
        if (dataType == null || dataType.isBlank()) {
            return null;
        }
        String normalized = dataType.trim().toLowerCase();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '(' || Character.isWhitespace(c)) {
                return i > 0 ? normalized.substring(0, i) : null;
            }
        }
        return normalized;
    }

    /** search_config.json 中的单条字段类型配置 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SearchConfigEntry {

        /** 数据库字段类型，如 varchar、datetime */
        private String dataType;

        /** 业务类型编码，如 TEXT、INT */
        private String businessType;

        /** 业务类型名称，如 文本、整数 */
        private String businessTypeName;

        /** 该字段类型下可用的搜索配置 */
        private List<SearchConfigItemResp> searchConfig;
    }
}
