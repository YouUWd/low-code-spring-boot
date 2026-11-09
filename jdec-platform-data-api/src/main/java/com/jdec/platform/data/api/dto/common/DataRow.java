package com.jdec.platform.data.api.dto.common;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业务数据行模型 (自相似视图数据树的行单元)
 *
 * <p>基于全链路统一 table.column 语言设计：
 *
 * <ul>
 *   <li>1. 物理表字段数据统一平铺为 {@code table.column -> value} (如 {@code "info.company_name": "腾讯"});
 *   <li>2. 级联子视图区块统一挂载在 {@code children} 中 (自相似递归嵌套).
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "业务数据行")
public class DataRow implements Serializable {

    @Schema(description = "从属于当前数据行的子视图数据区块集合 (自相似递归嵌套)")
    @JsonProperty("children")
    private List<DataBlock> children;

    /** 动态承载物理表数据 (支持 "table.column" -> value 扁平键，或 "table" -> Map 嵌套键) */
    @JsonIgnore @Builder.Default private Map<String, Object> tables = new LinkedHashMap<>();

    public DataRow(Map<String, Object> map) {
        if (map != null) {
            this.tables = new LinkedHashMap<>(map);
        }
    }

    public static DataRow of(Map<String, Object> map) {
        return new DataRow(map);
    }

    /** 序列化时统一输出扁平的 table.column 结构 */
    @JsonAnyGetter
    public Map<String, Object> getFlatFields() {
        if (tables == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> flat = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : tables.entrySet()) {
            if ("children".equals(entry.getKey())) {
                continue;
            }
            if (entry.getValue() instanceof Map<?, ?> subMap) {
                for (Map.Entry<?, ?> subEntry : subMap.entrySet()) {
                    flat.put(entry.getKey() + "." + subEntry.getKey(), subEntry.getValue());
                }
            } else {
                flat.put(entry.getKey(), entry.getValue());
            }
        }
        return flat;
    }

    private void ensureTablesMutable() {
        if (tables == null) {
            tables = new LinkedHashMap<>();
        } else if (!(tables instanceof LinkedHashMap)) {
            tables = new LinkedHashMap<>(tables);
        }
    }

    @JsonAnySetter
    public void setField(String key, Object value) {
        ensureTablesMutable();
        if (!"children".equals(key)) {
            put(key, value);
        }
    }

    public void setTable(String key, Object value) {
        setField(key, value);
    }

    /** 获取指定物理表名下的列数据 Map */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTable(String tableName) {
        if (tables == null || tableName == null) {
            return null;
        }
        // 1. 若本身存有以 tableName 为键的嵌套 Map
        Object val = tables.get(tableName);
        if (val instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        // 2. 否则从扁平 "table.column" 中聚合并返回该表的 Map
        Map<String, Object> subMap = new LinkedHashMap<>();
        String prefix = tableName.trim() + ".";
        for (Map.Entry<String, Object> entry : tables.entrySet()) {
            if (entry.getKey() != null && entry.getKey().startsWith(prefix)) {
                subMap.put(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }
        return subMap.isEmpty() ? null : subMap;
    }

    /** 设置指定物理表名下的列数据 */
    public void putTable(String tableName, Map<String, Object> columnData) {
        ensureTablesMutable();
        if (columnData != null && tableName != null) {
            String t = tableName.trim();
            for (Map.Entry<String, Object> entry : columnData.entrySet()) {
                if (entry.getKey() != null) {
                    tables.put(t + "." + entry.getKey().trim(), entry.getValue());
                }
            }
            tables.put(t, columnData);
        }
    }

    /** 获取指定表名和字段名的值 (优先匹配 table.column 扁平键) */
    public Object getValue(String tableName, String column) {
        if (tables == null) {
            return null;
        }
        if (tableName != null && column != null) {
            String key = tableName.trim() + "." + column.trim();
            if (tables.containsKey(key)) {
                return tables.get(key);
            }
            Map<String, Object> table = getTable(tableName);
            if (table != null && table.containsKey(column.trim())) {
                return table.get(column.trim());
            }
        }
        return column != null ? tables.get(column.trim()) : null;
    }

    /** 获取指定 table.column 格式的值 */
    public Object getValue(String tableDotColumn) {
        if (tableDotColumn == null || tables == null) {
            return null;
        }
        if (tables.containsKey(tableDotColumn)) {
            return tables.get(tableDotColumn);
        }
        if (tableDotColumn.contains(".")) {
            int dot = tableDotColumn.indexOf('.');
            return getValue(tableDotColumn.substring(0, dot), tableDotColumn.substring(dot + 1));
        }
        return tables.get(tableDotColumn);
    }

    public String getString(String tableName, String column) {
        Object val = getValue(tableName, column);
        return val != null ? String.valueOf(val) : null;
    }

    public Long getLong(String tableName, String column) {
        Object val = getValue(tableName, column);
        if (val instanceof Number n) {
            return n.longValue();
        }
        if (val instanceof String s && !s.isBlank()) {
            return Long.parseLong(s.trim());
        }
        return null;
    }

    public Integer getInteger(String tableName, String column) {
        Object val = getValue(tableName, column);
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof String s && !s.isBlank()) {
            return Integer.parseInt(s.trim());
        }
        return null;
    }

    /** 设置物理表单列值 (以 table.column 格式存储) */
    public DataRow putValue(String tableName, String column, Object value) {
        ensureTablesMutable();

        String col = column != null ? column.trim() : "";
        if (tableName != null && !tableName.isBlank()) {
            String t = tableName.trim();
            tables.put(t + "." + col, value);
            Object obj = tables.get(t);
            if (obj instanceof Map<?, ?> subMap) {
                try {
                    ((Map<String, Object>) subMap).put(col, value);
                } catch (UnsupportedOperationException e) {
                    Map<String, Object> mutableSub = new LinkedHashMap<>();
                    subMap.forEach((k, v) -> mutableSub.put(String.valueOf(k), v));
                    mutableSub.put(col, value);
                    tables.put(t, mutableSub);
                }
            } else {
                Map<String, Object> mutableSub = new LinkedHashMap<>();
                mutableSub.put(col, value);
                tables.put(t, mutableSub);
            }
        } else {
            tables.put(col, value);
        }
        return this;
    }

    /** 直接设置 table.column 键值对 */
    public DataRow put(String key, Object value) {
        ensureTablesMutable();
        if (key != null) {
            tables.put(key, value);
            if (key.contains(".")) {
                int dot = key.indexOf('.');
                String t = key.substring(0, dot).trim();
                String col = key.substring(dot + 1).trim();
                Object obj = tables.get(t);
                if (obj instanceof Map<?, ?> subMap) {
                    try {
                        ((Map<String, Object>) subMap).put(col, value);
                    } catch (UnsupportedOperationException e) {
                        Map<String, Object> mutableSub = new LinkedHashMap<>();
                        subMap.forEach((k, v) -> mutableSub.put(String.valueOf(k), v));
                        mutableSub.put(col, value);
                        tables.put(t, mutableSub);
                    }
                } else {
                    Map<String, Object> mutableSub = new LinkedHashMap<>();
                    mutableSub.put(col, value);
                    tables.put(t, mutableSub);
                }
            }
        }
        return this;
    }

    // ==================== 子视图区块管理 ====================

    /** 根据子视图编码查找首个匹配的子区块 */
    public DataBlock getChild(String view) {
        if (children == null || children.isEmpty() || view == null || view.isBlank()) {
            return null;
        }
        return children.stream()
                .filter(b -> view.equalsIgnoreCase(b.getView()))
                .findFirst()
                .orElse(null);
    }

    /** 根据数字 ID 或编码查找首个匹配的子区块 (兼容旧测试或以 ID 命名的视图) */
    public DataBlock getChild(Long id) {
        if (id == null) {
            return null;
        }
        return getChild(String.valueOf(id));
    }

    /** 根据子视图编码获取所有匹配的子区块 */
    public List<DataBlock> getChildren(String view) {
        if (children == null || children.isEmpty() || view == null || view.isBlank()) {
            return Collections.emptyList();
        }
        return children.stream()
                .filter(b -> view.equalsIgnoreCase(b.getView()))
                .collect(Collectors.toList());
    }

    /** 追加挂载子视图数据区块 */
    public DataRow addChild(DataBlock block) {
        if (block == null) {
            return this;
        }
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(block);
        return this;
    }
}
