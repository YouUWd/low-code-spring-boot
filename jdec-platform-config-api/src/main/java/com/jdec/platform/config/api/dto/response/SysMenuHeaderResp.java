package com.jdec.platform.config.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@Schema(description = "菜单表头节点响应")
public class SysMenuHeaderResp {

    @Schema(description = "主键id")
    private Long id;

    @Schema(description = "虚拟id(类型-id)，如 MENU_47 / Field_10")
    private String virId;

    @Schema(description = "父节点虚拟id(顶层为 0)")
    private String parentId;

    @Schema(description = "所属菜单ID（首次生成时统一为最顶层菜单ID）", example = "1")
    private Long menuId;

    @Schema(description = "显示名（按 sourceId 取源头：菜单节点取菜单标题，字段节点取模块字段显示名）", example = "外部对象列表")
    private String label;

    @Schema(description = "节点类型：0-菜单，1-字段", example = "0")
    private Integer nodeType;

    @Schema(description = "来源ID（nodeType=0 时=来源菜单ID，nodeType=1 时=来源字段ID）", example = "1")
    private Long sourceId;

    @Schema(description = "模块id（nodeType=0 时=菜单绑定模块id，nodeType=1 时=字段所属模块id）", example = "1001")
    private Long moduleId;

    @Schema(description = "字段索引（模块id.表名.字段名）", example = "1001.info.company_name")
    private String dataIndex;

    @Schema(
            description = "是否关联字段（仅字段节点输出）：0-否，1-是（sys_field 中该表名+列名配置了 relation_business_no）",
            example = "1")
    private Integer relationWord;

    @Schema(description = "菜单显示颜色")
    private List<String> colors;

    @Schema(description = "列宽(px)")
    private Integer width;

    @Schema(description = "可排序：0-否，1-是", example = "1")
    private Integer sorted;

    @Schema(description = "搜索控件类型")
    private String searchType;

    @Schema(description = "业务类型编码（仅字段节点输出，取自 sys_field 关联配置）", example = "contract")
    private String businessTypeCode;

    @Schema(description = "文本是否省略：0-否，1-是", example = "0")
    private Integer ellipsis;

    @Schema(description = "固定：left-左固定，right-右固定，空-不固定")
    private String fixed;

    @Schema(description = "是否选中：0-否，1-是", example = "0")
    private Integer checked;

    @Schema(description = "排序值")
    private Integer sortValue;

    @Schema(description = "子节点列表")
    private List<SysMenuHeaderResp> children;
}
