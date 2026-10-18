package com.jdec.platform.common.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 基于物理表与列名的平铺排序规则项 (FlatSortItem)
 *
 * <p>适用于使用方未知 fieldId、仅知晓当前菜单及其子孙菜单下物理表与字段的查询场景。 支持通过可选的 menuId 指定靠近的菜单以解决跨菜单同名表歧义。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "基于物理表与列名的平铺排序规则项")
public class FlatSortItem implements Serializable {

    @Schema(description = "可选: 最靠近该物理表的菜单 ID (用于多级同名表精准定位；缺省时由后端自动推导)", example = "1001")
    private Long menuId;

    @Schema(description = "物理表名 (单表或主表查询时可缺省)", example = "info")
    private String tableName;

    @NotBlank(message = "排序列名不能为空")
    @Schema(
            description = "物理列名 (如 id, created_date)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "id")
    private String columnName;

    @Schema(description = "排序方向: ASC (升序), DESC (降序)，默认 DESC", example = "DESC")
    @Builder.Default
    private String direction = "DESC";

    public static FlatSortItem asc(String tableName, String columnName) {
        return FlatSortItem.builder()
                .tableName(tableName)
                .columnName(columnName)
                .direction("ASC")
                .build();
    }

    public static FlatSortItem desc(String tableName, String columnName) {
        return FlatSortItem.builder()
                .tableName(tableName)
                .columnName(columnName)
                .direction("DESC")
                .build();
    }
}
