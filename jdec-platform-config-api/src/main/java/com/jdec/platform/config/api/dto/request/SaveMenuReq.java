package com.jdec.platform.config.api.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 保存菜单（支持整棵菜单树一并提交）请求 DTO，结构对齐 SaveStatusReq */
@Data
@Schema(description = "保存菜单请求")
public class SaveMenuReq {

    /** 本次新增/编辑的菜单节点 */
    @Schema(description = "本次新增/编辑的菜单节点")
    private SysMenuSaveReq menu;

    /** 整棵菜单树的扁平节点位置（可选，传入时同步批量更新父子关系与同级排序） */
    @Schema(description = "整棵菜单树的扁平节点位置（可选，传入时同步更新父子关系与同级排序）")
    private List<MenuTreeNode> treeNodes;

    /** 多级表头节点树根（菜单关联模块未变化时按节点是否已保存执行批量更新/新增） */
    @Schema(description = "多级表头节点树根")
    private MenuHeaderNode header;

    /** 菜单树节点位置信息 DTO */
    @Data
    @Schema(description = "菜单树节点位置信息")
    public static class MenuTreeNode {

        @Schema(description = "菜单ID（本次新增的当前节点传空或0，将替换为保存后生成的ID）")
        private Long id;

        @Schema(description = "父菜单ID（0表示根节点）")
        private Long parentId;

        @Schema(description = "同级排序序号")
        private Integer sortOrder;
    }

    /** 多级表头节点 DTO（树形结构） */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "多级表头节点")
    public static class MenuHeaderNode {

        @Schema(description = "主键id（已保存的节点带值，新增节点传空）")
        private Long id;

        @Schema(description = "虚拟id(类型-id)，如 MENU_47 / Field_10")
        private String virId;

        @Schema(description = "父节点虚拟id(顶层为 0)")
        private String parentId;

        @Schema(description = "所属菜单ID（保存时以入参菜单ID为准）", example = "1")
        private Long menuId;

        @Schema(description = "显示名（覆盖值或默认取菜单名/字段显示名）", example = "外部对象列表")
        private String label;

        @Schema(description = "节点类型：0-菜单，1-字段", example = "0")
        private Integer nodeType;

        @Schema(description = "来源ID（nodeType=0 时=来源菜单ID，nodeType=1 时=来源字段ID）", example = "1")
        private Long sourceId;

        @Schema(description = "模块id（nodeType=0 时=菜单绑定模块id，nodeType=1 时=字段所属模块id）", example = "1001")
        private Long moduleId;

        @Schema(description = "字段索引（模块id.表名.字段名）", example = "1001.info.company_name")
        private String dataIndex;

        @Schema(description = "菜单显示颜色（入库时序列化为 JSON 串）")
        private List<String> colors;

        @Schema(description = "列宽(px)")
        private Integer width;

        @Schema(description = "可排序：0-否，1-是", example = "1")
        private Integer sorted;

        @Schema(description = "搜索控件类型")
        private String searchType;

        @Schema(description = "文本是否省略：0-否，1-是", example = "0")
        private Integer ellipsis;

        @Schema(description = "固定：left-左固定，right-右固定，空-不固定")
        private String fixed;

        @Schema(description = "是否选中：0-否，1-是", example = "0")
        private Integer checked;

        @Schema(description = "排序值")
        private Integer sortValue;

        @Schema(description = "子节点列表")
        private List<MenuHeaderNode> children;
    }
}
