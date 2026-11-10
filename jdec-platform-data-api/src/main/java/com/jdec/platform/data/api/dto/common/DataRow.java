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
 *   <li>1. 物理表字段数据在 JSON 序列化时统一平铺为 {@code table.column -> value} (如 {@code "info.company_name":
 *       "腾讯"});
 *   <li>2. 内部存储以物理表名分组收敛 ({@code tableName -> Map<column, value>})，消除扁平键与嵌套表 Map 双写失步问题；
 *   <li>3. 级联子视图区块统一挂载在 {@code children} 中 (自相似递归嵌套).
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

    /** 动态承载物理表数据 (带表名字段归一化存储为 "table" -> Map<column, value>，无表名字段直接存为 key -> value) */
    @JsonIgnore @Builder.Default private Map<String, Object> tables = new LinkedHashMap<>();

    public DataRow(Map<String, Object> map) {
        this.tables = new LinkedHashMap<>();
        if (map != null) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    put(entry.getKey(), entry.getValue());
                }
            }
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
        String t = tableName.trim();
        // 1. 若本身存有以 tableName 为键的嵌套 Map
        Object val = tables.get(t);
        if (val instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        // 2. 兼容兜底：若外部直接向 tables 注入了扁平 "table.column" 键，则聚合并返回该表的 Map
        Map<String, Object> subMap = new LinkedHashMap<>();
        String prefix = t + ".";
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
            Map<String, Object> mutableSub = new LinkedHashMap<>();
            Object existing = tables.get(t);
            if (existing instanceof Map<?, ?> existingMap) {
                existingMap.forEach(
                        (k, v) -> {
                            if (k != null) {
                                mutableSub.put(String.valueOf(k).trim(), v);
                            }
                        });
            }
            for (Map.Entry<String, Object> entry : columnData.entrySet()) {
                if (entry.getKey() != null) {
                    String col = entry.getKey().trim();
                    mutableSub.put(col, entry.getValue());
                    tables.remove(t + "." + col);
                }
            }
            tables.put(t, mutableSub);
        }
    }

    /** 获取指定表名和字段名的值 */
    public Object getValue(String tableName, String column) {
        if (tables == null) {
            return null;
        }
        if (tableName != null && !tableName.isBlank() && column != null) {
            String t = tableName.trim();
            String col = column.trim();
            Map<String, Object> table = getTable(t);
            if (table != null && table.containsKey(col)) {
                return table.get(col);
            }
            String flatKey = t + "." + col;
            if (tables.containsKey(flatKey)) {
                return tables.get(flatKey);
            }
        }
        return column != null ? tables.get(column.trim()) : null;
    }

    /** 获取指定 table.column 格式的值 */
    public Object getValue(String tableDotColumn) {
        if (tableDotColumn == null || tables == null) {
            return null;
        }
        if (tableDotColumn.contains(".")) {
            int dot = tableDotColumn.indexOf('.');
            Object v =
                    getValue(tableDotColumn.substring(0, dot), tableDotColumn.substring(dot + 1));
            if (v != null) {
                return v;
            }
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

    /** 设置物理表单列值 (收敛存储于 tables.get(tableName) Map 中，消除双写失步) */
    @SuppressWarnings("unchecked")
    public DataRow putValue(String tableName, String column, Object value) {
        ensureTablesMutable();

        String col = column != null ? column.trim() : "";
        if (tableName != null && !tableName.isBlank()) {
            String t = tableName.trim();
            if (col.toLowerCase().startsWith(t.toLowerCase() + ".")) {
                col = col.substring(t.length() + 1).trim();
            }
            tables.remove(t + "." + col);
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
            put(col, value);
        }
        return this;
    }

    /** 直接设置 table.column 键值对或物理表 Map */
    @SuppressWarnings("unchecked")
    public DataRow put(String key, Object value) {
        ensureTablesMutable();
        if (key == null) {
            return this;
        }
        String trimmedKey = key.trim();
        if (trimmedKey.contains(".") && !trimmedKey.startsWith("_fk_")) {
            int dot = trimmedKey.indexOf('.');
            String t = trimmedKey.substring(0, dot).trim();
            String col = trimmedKey.substring(dot + 1).trim();
            return putValue(t, col, value);
        }
        if (value instanceof Map<?, ?> subMap && !trimmedKey.matches("\\d+")) {
            Map<String, Object> casted = new LinkedHashMap<>();
            subMap.forEach(
                    (k, v) -> {
                        if (k != null) {
                            casted.put(String.valueOf(k), v);
                        }
                    });
            putTable(trimmedKey, casted);
            return this;
        }
        tables.put(trimmedKey, value);
        return this;
    }

    // ==================== 子视图区块管理 ====================

    /** 根据子视图编码查找首个匹配的子区块 */
    public DataBlock getChild(String view) {
        if (children == null || children.isEmpty() || view == null || view.isBlank()) {
            return null;
        }
        return children.stream()
                .filter(b -> b != null && view.equalsIgnoreCase(b.getView()))
                .findFirst()
                .orElse(null);
    }

    /** 根据数字 ID 转换视图编码查找首个匹配的子区块 */
    public DataBlock getChild(Long id) {
        return id == null ? null : getChild(String.valueOf(id));
    }

    /** 根据子视图编码获取所有匹配的子区块 */
    public List<DataBlock> getChildren(String view) {
        if (children == null || children.isEmpty() || view == null || view.isBlank()) {
            return Collections.emptyList();
        }
        return children.stream()
                .filter(b -> b != null && view.equalsIgnoreCase(b.getView()))
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
