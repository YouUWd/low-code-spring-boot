package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 保存状态（支持整棵状态树一并提交）请求 DTO，结构对齐 SaveModuleReq */
@Data
@Schema(description = "保存状态请求")
public class SaveStatusReq {

    /** 本次新增/编辑的状态节点 */
    @Schema(description = "本次新增/编辑的状态节点")
    private SysStatusSaveReq status;

    /** 整棵状态树的扁平节点位置（可选，传入时同步批量更新父子关系与同级排序） */
    @Schema(description = "整棵状态树的扁平节点位置（可选，传入时同步更新父子关系与同级排序）")
    private List<StatusTreeNode> treeNodes;

    /** 状态树节点位置信息 DTO */
    @Data
    @Schema(description = "状态树节点位置信息")
    public static class StatusTreeNode {

        @Schema(description = "状态ID（本次新增的当前节点传空或0，将替换为保存后生成的ID）")
        private Long id;

        @Schema(description = "父状态ID（0表示根节点）")
        private Long pid;

        @Schema(description = "同级排序序号")
        private Integer sortOrder;
    }
}
