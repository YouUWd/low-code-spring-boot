package com.jdec.platform.config.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多级列头展示节点
 *
 * <p>菜单节点（nodeType=0）输出 sourceId/moduleId/label/colors/children；字段节点（nodeType=1）输出
 * label/fieldId/moduleId/dataIndex/relationWord/width/sorted/searchType/ellipsis/fixed/sortValue。未涉及的字段为
 * null 时不输出（label 按 sourceId 取源头，不取库中存储值）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "多级列头展示节点")
public class MenuHeaderColumnResp {

    @Schema(description = "菜单节点来源ID（nodeType=0 时为来源菜单ID）")
    private Long sourceId;

    @Schema(description = "列头显示名", example = "外部对象管理")
    private String label;

    @Schema(description = "菜单显示颜色（JSON 数组）")
    private List<String> colors;

    @Schema(description = "字段节点来源ID（nodeType=1 时为来源字段ID）", example = "10001")
    private Long fieldId;

    @Schema(description = "模块id（nodeType=0 时=菜单绑定模块id，nodeType=1 时=字段所属模块id）", example = "1001")
    private Long moduleId;

    @Schema(description = "字段索引（模块id.表名.字段名）", example = "1001.info.company_name")
    private String dataIndex;

    @Schema(
            description = "是否关联字段（仅字段节点输出）：0-否，1-是（sys_field 中该表名+列名配置了 relation_business_no）",
            example = "1")
    private Integer relationWord;

    @Schema(description = "节点类型：0-菜单 / 1-字段")
    private Integer nodeType;

    @Schema(description = "列宽(px)")
    private Integer width;

    @Schema(description = "可排序：0-否，1-是")
    private Integer sorted;

    @Schema(description = "搜索控件类型")
    private MenuHeaderSearchTypeResp searchType;

    @Schema(description = "文本是否省略：0-否，1-是")
    private Integer ellipsis;

    @Schema(description = "固定：left-左固定，right-右固定，空-不固定")
    private String fixed;

    @Schema(description = "排序值")
    private Integer sortValue;

    @Schema(description = "子节点列表")
    private List<MenuHeaderColumnResp> children;
}
