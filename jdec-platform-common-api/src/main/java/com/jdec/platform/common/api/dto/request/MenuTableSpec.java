package com.jdec.platform.common.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜单物理表投影规格 (MenuTableSpec)
 *
 * <p>用于使用方通过物理表名和字段列表指定投影列。 支持通过可选的 menuId 指定靠近的菜单以解决跨菜单同名表歧义。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "菜单物理表投影规格")
public class MenuTableSpec implements Serializable {

    @Schema(description = "可选: 最靠近该物理表的菜单 ID (用于多级同名表精准定位；缺省时由后端自动推导)", example = "1001")
    private Long menuId;

    @NotBlank(message = "物理表名不能为空")
    @Schema(
            description = "物理表名 (如 info, basic_info)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "info")
    private String tableName;

    @Schema(
            description = "物理字段名列表 (为 null 或空时代表投影该表在模块元数据定义中的所有列)",
            example = "[\"id\", \"company_name\"]")
    private List<String> columns;
}
