package com.jdec.platform.config.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 列表表头列配置信息 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "列表表头列配置信息")
public class ModuleTableHeaderDTO {

    /** 表头ID（更新时使用） */
    @Schema(description = "表头ID（更新时使用）", example = "1")
    private Long id;

    /** 列名称 */
    @Schema(description = "列名称", example = "学号")
    private String name;

    /** 列名称 */
    @Schema(description = "表名称", example = "student")
    private String table;

    /** 关联字段 */
    @Schema(description = "关联字段", example = "student_no")
    private String field;

    /** 列宽度（像素） */
    @Schema(description = "列宽度（像素）", example = "100")
    private Integer width;

    /** 排序顺序 */
    @Schema(description = "排序顺序", example = "1")
    private Integer sortOrder;

    /** 搜索类型 */
    @Schema(description = "搜索类型", example = "input")
    private String searchType;

    /** 固定列位置 */
    @Schema(
            description = "固定列位置",
            example = "none",
            allowableValues = {"none", "left", "right"})
    private String fixed;

    /** 文本超出是否省略 */
    @Schema(description = "文本超出是否省略", example = "true")
    private Boolean ellipsis;

    /** 是否可排序 */
    @Schema(description = "是否可排序", example = "true")
    private Boolean sortable;

    /** 模块血缘寻址路径（自根模块至当前字段所在模块的ID链条，用于多层级嵌套消歧与确定性寻址） */
    @Schema(description = "模块血缘寻址路径", example = "[101, 104, 106]")
    private List<Long> modulePath;
}
