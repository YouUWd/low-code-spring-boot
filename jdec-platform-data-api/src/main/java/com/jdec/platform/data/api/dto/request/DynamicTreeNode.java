package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 动态模块树通用自相似抽象节点
 *
 * <p>定义模块树节点最基础的两个特征：模块唯一标识与自相似下级树结构。
 *
 * @param <T> 具体子节点类型，支持自递归
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "动态模块树通用自相似抽象节点")
public abstract class DynamicTreeNode<T extends DynamicTreeNode<T>> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(
            description = "模块 ID (可选，若不传则根据 fields 自动路由推导)",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            example = "101")
    private Long moduleId;

    @Schema(description = "下级子模块树 (自相似递归嵌套，可选)")
    private List<T> children;
}
