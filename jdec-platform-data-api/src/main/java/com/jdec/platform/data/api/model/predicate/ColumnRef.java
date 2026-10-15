package com.jdec.platform.data.api.model.predicate;

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
@Schema(description = "物理列引用表达式")
public class ColumnRef implements FieldExpression {

    @Schema(description = "所属模块 ID (可选)", example = "1001")
    private Long moduleId;

    @Schema(description = "物理表名 (可选，省略时根据主模块推导)", example = "info")
    private String tableName;

    @Schema(description = "物理列名", example = "company_name")
    private String columnName;

    @Override
    public String getExprType() {
        return "column";
    }

    public static ColumnRef of(String tableName, String columnName) {
        return ColumnRef.builder().tableName(tableName).columnName(columnName).build();
    }

    public static ColumnRef of(Long moduleId, String tableName, String columnName) {
        return ColumnRef.builder()
                .moduleId(moduleId)
                .tableName(tableName)
                .columnName(columnName)
                .build();
    }

    public static ColumnRef of(String columnName) {
        if (columnName != null && columnName.contains(".")) {
            String[] parts = columnName.split("\\.", 2);
            return of(parts[0], parts[1]);
        }
        return of(null, columnName);
    }
}
