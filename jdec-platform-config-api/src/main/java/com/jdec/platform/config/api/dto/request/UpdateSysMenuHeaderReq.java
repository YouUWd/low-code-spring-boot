package com.jdec.platform.config.api.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 菜单表头节点展示配置更新请求项（字段均非必填，未传字段保持原值） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "菜单表头节点展示配置更新请求项")
public class UpdateSysMenuHeaderReq {

    @Schema(description = "节点主键id", example = "1")
    private Long id;

    @Schema(description = "菜单显示颜色")
    private String colors;

    @Schema(description = "列宽(px)")
    private Integer width;

    @Schema(description = "可排序：0-否，1-是")
    private Integer sorted;

    @Schema(description = "搜索控件类型")
    private String searchType;

    @Schema(description = "文本是否省略：0-否，1-是")
    private Integer ellipsis;
}
