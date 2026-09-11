package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 保存配置分类（支持整棵混合树一并提交）请求 DTO，结构对齐 SaveStatusReq */
@Data
@Schema(description = "保存配置分类请求")
public class SaveConfigCategoryReq {

    /** 本次新增/编辑的配置分类 */
    @Schema(description = "本次新增/编辑的配置分类")
    private SysConfigCategorySaveReq config;

    /** 整棵混合树的扁平节点位置（可选，传入时同步批量更新父子关系与同级排序） */
    @Schema(description = "整棵混合树的扁平节点位置（可选，传入时同步更新父子关系与同级排序）")
    private List<ConfigTreeNode> treeNodes;
}
