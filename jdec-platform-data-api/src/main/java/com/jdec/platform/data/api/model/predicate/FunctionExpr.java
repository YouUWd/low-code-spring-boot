package com.jdec.platform.data.api.model.predicate;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** SQL 函数表达式 (FunctionExpr)，如 JSON_EXTRACT(col, '$.title') 或 DISTINCT(col) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "函数调用表达式")
public class FunctionExpr implements FieldExpression {

    @Schema(description = "函数名称 (如 JSON_EXTRACT, COALESCE, DISTINCT 等)", example = "JSON_EXTRACT")
    private String functionName;

    @Schema(description = "主目标列/字段引用")
    private ColumnRef targetColumn;

    @Schema(description = "函数附加实参列表")
    @Builder.Default
    private List<Object> arguments = new ArrayList<>();

    @Override
    public String getExprType() {
        return "function";
    }

    public static FunctionExpr jsonExtract(String tableName, String columnName, String jsonPath) {
        return FunctionExpr.builder()
                .functionName("JSON_EXTRACT")
                .targetColumn(ColumnRef.of(tableName, columnName))
                .arguments(List.of(jsonPath))
                .build();
    }

    public static FunctionExpr distinct(String tableName, String columnName) {
        return FunctionExpr.builder()
                .functionName("DISTINCT")
                .targetColumn(ColumnRef.of(tableName, columnName))
                .build();
    }
}
