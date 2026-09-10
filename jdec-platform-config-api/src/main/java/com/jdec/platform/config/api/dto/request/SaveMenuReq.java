package com.jdec.platform.config.api.dto.request;

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
}
