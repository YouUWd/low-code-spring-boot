package com.jdec.platform.data.api.dto.common;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业务数据行模型 (自相似模块数据树的行单元)
 *
 * <p>包含当前行物理表字段数据（以表名分组，如 {"info": {...}}）以及从属于当前行的自相似子模块数据区块列表（children）。
 *
 * <p>支持直接通过动态属性序列化/反序列化（即 JSON 顶层键直接为物理表名和 children）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "业务数据行")
public class DataRow implements Serializable {

    @Schema(description = "从属于当前数据行的子模块数据区块集合 (自相似递归嵌套)")
    private List<DataBlock> children;

    /** 动态承载物理表空间数据（例如 info -> Map, basic_info -> Map） */
    @Builder.Default private Map<String, Object> tables = new LinkedHashMap<>();

    public DataRow(Map<String, Object> map) {
        if (map != null) {
            this.tables = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                setTable(entry.getKey(), entry.getValue());
            }
        }
    }

    @JsonAnyGetter
    public Map<String, Object> getTables() {
        return tables;
    }

    @JsonAnySetter
    public void setTable(String key, Object value) {
        if (tables == null) {
            tables = new LinkedHashMap<>();
        }
        if (!"children".equals(key)) {
            tables.put(key, value);
        }
    }

    /** 获取指定物理表名下的列数据 Map (若不存在返回 null) */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTable(String tableName) {
        if (tables == null) {
            return null;
        }
        Object val = tables.get(tableName);
        return val instanceof Map<?, ?> ? (Map<String, Object>) val : null;
    }

    /** 设置指定物理表名下的列数据 */
    public void putTable(String tableName, Map<String, Object> columnData) {
        if (tables == null) {
            tables = new LinkedHashMap<>();
        }
        tables.put(tableName, columnData);
    }

    /** 获取指定表名和字段名的值 */
    public Object getValue(String tableName, String column) {
        Map<String, Object> table = getTable(tableName);
        return table != null ? table.get(column) : null;
    }

    /** 获取字符串类型字段值 */
    public String getString(String tableName, String column) {
        Object val = getValue(tableName, column);
        return val != null ? String.valueOf(val) : null;
    }

    /** 获取 Long 类型字段值 */
    public Long getLong(String tableName, String column) {
        Object val = getValue(tableName, column);
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        if (val instanceof String && !((String) val).isBlank()) {
            return Long.parseLong((String) val);
        }
        return null;
    }

    /** 获取 Integer 类型字段值 */
    public Integer getInteger(String tableName, String column) {
        Object val = getValue(tableName, column);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        if (val instanceof String && !((String) val).isBlank()) {
            return Integer.parseInt((String) val);
        }
        return null;
    }

    /** 设置指定物理表下的单个字段值 (自动初始化表 Map) */
    public DataRow putValue(String tableName, String column, Object value) {
        if (tables == null) {
            tables = new LinkedHashMap<>();
        }
        Map<String, Object> table = getTable(tableName);
        if (table == null) {
            table = new LinkedHashMap<>();
            tables.put(tableName, table);
        }
        table.put(column, value);
        return this;
    }

    /** 根据子模块 ID 查找首个匹配的子区块 */
    public DataBlock getChild(Long childModuleId) {
        if (children == null || children.isEmpty() || childModuleId == null) {
            return null;
        }
        return children.stream()
                .filter(b -> childModuleId.equals(b.getModuleId()))
                .findFirst()
                .orElse(null);
    }

    /** 根据子模块 ID 获取所有匹配的子区块 */
    public List<DataBlock> getChildren(Long childModuleId) {
        if (children == null || children.isEmpty() || childModuleId == null) {
            return Collections.emptyList();
        }
        return children.stream()
                .filter(b -> childModuleId.equals(b.getModuleId()))
                .collect(java.util.stream.Collectors.toList());
    }

    /** 追加挂载子模块数据区块 */
    public DataRow addChild(DataBlock block) {
        if (block == null) {
            return this;
        }
        if (children == null) {
            children = new java.util.ArrayList<>();
        }
        children.add(block);
        return this;
    }
}
