package com.jdec.platform.data.api.model.predicate;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 原生 SQL 片段谓词（RawSqlPredicate），用于安全参数化的高阶 SQL 表达式 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "原生安全参数化 SQL 片段谓词")
public class RawSqlPredicate implements Predicate {

    @Schema(description = "参数化 SQL 模板 (占位符可使用 {0}, {1} 或 ?)", example = "JSON_CONTAINS({0}, {1})")
    private String sqlTemplate;

    @Schema(description = "绑定参数列表")
    @Builder.Default
    private List<Object> params = new ArrayList<>();

    @Override
    public String getType() {
        return "raw";
    }

    public static RawSqlPredicate of(String sqlTemplate, Object... params) {
        return RawSqlPredicate.builder()
                .sqlTemplate(sqlTemplate)
                .params(params != null ? List.of(params) : List.of())
                .build();
    }
}
