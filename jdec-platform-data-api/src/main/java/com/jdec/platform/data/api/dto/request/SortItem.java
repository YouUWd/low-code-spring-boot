package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 结构化排序规则项 (SortItem，基于物理表名与列名) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "结构化排序规则项")
public class SortItem implements Serializable {

    @Schema(description = "所属模块 ID (多模块树查询时建议指定，单表查询时可缺省)", example = "1001")
    private Long moduleId;

    @Schema(description = "物理表名 (单表或根表查询时可缺省)", example = "info")
    private String tableName;

    @NotNull(message = "排序列名不能为空")
    @Schema(
            description = "物理列名 (如 id, company_name)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "id")
    private String columnName;

    @Schema(description = "排序方向: ASC (升序), DESC (降序)，默认 DESC", example = "DESC")
    @Builder.Default
    private String direction = "DESC";

    public static SortItem asc(Long moduleId, String tableName, String columnName) {
        return SortItem.builder()
                .moduleId(moduleId)
                .tableName(tableName)
                .columnName(columnName)
                .direction("ASC")
                .build();
    }

    public static SortItem asc(Long moduleId, String columnName) {
        return asc(moduleId, null, columnName);
    }

    public static SortItem asc(String tableName, String columnName) {
        return asc(null, tableName, columnName);
    }

    public static SortItem asc(String columnName) {
        return asc(null, null, columnName);
    }

    public static SortItem desc(Long moduleId, String tableName, String columnName) {
        return SortItem.builder()
                .moduleId(moduleId)
                .tableName(tableName)
                .columnName(columnName)
                .direction("DESC")
                .build();
    }

    public static SortItem desc(Long moduleId, String columnName) {
        return desc(moduleId, null, columnName);
    }

    public static SortItem desc(String tableName, String columnName) {
        return desc(null, tableName, columnName);
    }

    public static SortItem desc(String columnName) {
        return desc(null, null, columnName);
    }
}
