package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 列表表头列配置
 */
@Data
@Schema(description = "列表表头列配置")
public class TableHeaderColumn {

    /**
     * 列名称
     */
    @Schema(description = "列名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "学号")
    private String name;

    /**
     * 关联字段（从simpleFields或combineFields选择）
     */
    @Schema(description = "关联字段（从simpleFields或combineFields选择）", requiredMode = Schema.RequiredMode.REQUIRED, example = "student_no")
    private String field;

    /**
     * 列宽度（像素）
     */
    @Schema(description = "列宽度（像素）", example = "100")
    private Integer width;

    /**
     * 排序顺序
     */
    @Schema(description = "排序顺序（数字越小越靠前）", example = "1")
    private Integer sortOrder;

    /**
     * 搜索类型
     */
    @Schema(description = "搜索类型", example = "text", allowableValues = {"text", "number", "date", "select", "range", "none"})
    private String searchType;

    /**
     * 固定列位置
     */
    @Schema(description = "固定列位置", example = "none", allowableValues = {"none", "left", "right"})
    private String fixed;

    /**
     * 文本超出是否省略
     */
    @Schema(description = "文本超出是否省略", example = "true")
    private Boolean ellipsis;

    /**
     * 是否可排序
     */
    @Schema(description = "是否可排序", example = "true")
    private Boolean sortable;
}
