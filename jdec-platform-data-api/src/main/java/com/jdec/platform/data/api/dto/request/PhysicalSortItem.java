package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 物理表排序规则项 (基于物理表名与列名) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "物理表排序规则项")
public class PhysicalSortItem implements Serializable {

    @NotNull(message = "所属模块 ID 不能为空")
    @Schema(description = "所属模块 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
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

    public static PhysicalSortItem asc(Long moduleId, String tableName, String columnName) {
        return PhysicalSortItem.builder()
                .moduleId(moduleId)
                .tableName(tableName)
                .columnName(columnName)
                .direction("ASC")
                .build();
    }

    public static PhysicalSortItem asc(Long moduleId, String columnName) {
        return asc(moduleId, null, columnName);
    }

    public static PhysicalSortItem desc(Long moduleId, String tableName, String columnName) {
        return PhysicalSortItem.builder()
                .moduleId(moduleId)
                .tableName(tableName)
                .columnName(columnName)
                .direction("DESC")
                .build();
    }

    public static PhysicalSortItem desc(Long moduleId, String columnName) {
        return desc(moduleId, null, columnName);
    }
}
