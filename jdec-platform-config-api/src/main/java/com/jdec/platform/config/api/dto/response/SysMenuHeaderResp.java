package com.jdec.platform.config.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 菜单表头节点响应（树形结构） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "菜单表头节点响应")
public class SysMenuHeaderResp {

    @Schema(description = "所属菜单ID", example = "1")
    private Long menuId;

    @Schema(description = "显示名（覆盖值或默认取菜单名/字段显示名）", example = "外部对象列表")
    private String label;

    @Schema(description = "节点类型：0-菜单，1-字段", example = "0")
    private Integer nodeType;

    @Schema(description = "来源菜单ID（nodeType=0 时返回）", example = "1")
    private Long sourceMenuId;

    @Schema(description = "来源字段ID（nodeType=1 时返回）", example = "11")
    private Long sourceFieldId;

    @Schema(description = "字段（列表数据字段名）", example = "name")
    private String field;

    @Schema(description = "菜单显示颜色")
    private String colors;

    @Schema(description = "列宽(px)")
    private Integer width;

    @Schema(description = "可排序：0-否，1-是", example = "1")
    private Integer sorted;

    @Schema(description = "搜索控件类型")
    private String searchType;

    @Schema(description = "文本是否省略：0-否，1-是", example = "0")
    private Integer ellipsis;

    @Schema(description = "是否选中：0-否，1-是", example = "0")
    private Integer checked;

    @Schema(description = "子节点列表（菜单节点返回，字段节点不返回）")
    private List<SysMenuHeaderResp> children;
}
