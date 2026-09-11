package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 通用配置混合树节点位置信息 DTO。
 *
 * <p>该树由配置分类（sys_config_category）与配置项（sys_config_item）两张表混合构成，故每个节点通过 {@code nodeType}
 * 标识其归属的表，后端按类型拆分后分别操作对应的表。
 */
@Data
@Schema(description = "通用配置混合树节点位置信息")
public class ConfigTreeNode {

    @Schema(description = "节点类型：category-配置分类, item-配置项")
    private String nodeType;

    @Schema(description = "节点ID（本次新增的当前节点传空或0，将替换为保存后生成的ID）")
    private Long id;

    @Schema(description = "父节点ID（配置分类固定为0；配置项0表示直接挂在分类下，否则为父配置项ID）")
    private Long parentId;

    @Schema(description = "同级排序序号（从0开始）")
    private Integer sort;
}
