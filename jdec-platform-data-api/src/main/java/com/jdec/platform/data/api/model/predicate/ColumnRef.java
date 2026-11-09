package com.jdec.platform.data.api.model.predicate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 物理列引用表达式 (ColumnRef) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "物理列引用表达式")
public class ColumnRef implements FieldExpression {

    @Schema(description = "物理表名 (可选，省略时根据主模块推导)", example = "info")
    private String tableName;

    @Schema(description = "物理列名", example = "company_name")
    private String columnName;

    @JsonIgnore
    @Override
    public String getExprType() {
        return "column";
    }

    @JsonIgnore
    public String getField() {
        if (tableName != null && !tableName.isBlank()) {
            return tableName.trim() + "." + (columnName != null ? columnName.trim() : "");
        }
        return columnName;
    }

    public void setField(String field) {
        if (field != null && field.contains(".")) {
            String[] parts = field.split("\\.", 2);
            this.tableName = parts[0].trim();
            this.columnName = parts[1].trim();
        } else {
            this.columnName = field != null ? field.trim() : null;
        }
    }

    public static ColumnRef of(String tableName, String columnName) {
        return ColumnRef.builder().tableName(tableName).columnName(columnName).build();
    }

    public static ColumnRef of(String fieldOrColumnName) {
        if (fieldOrColumnName != null && fieldOrColumnName.contains(".")) {
            String[] parts = fieldOrColumnName.split("\\.", 2);
            return of(parts[0].trim(), parts[1].trim());
        }
        return of(null, fieldOrColumnName != null ? fieldOrColumnName.trim() : null);
    }
}
