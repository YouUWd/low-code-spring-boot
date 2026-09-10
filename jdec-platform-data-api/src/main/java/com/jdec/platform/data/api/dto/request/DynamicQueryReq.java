package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * 动态列表/全景分页查询请求模型 (自相似模块查询树)
 *
 * <p>模块树负责表达业务数据空间拓扑，每个节点拥有独立的查询意图与下级子节点展开能力。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "动态列表/全景分页查询请求模型 (自相似模块查询树)")
public class DynamicQueryReq extends DynamicTreeNode<DynamicQueryReq> {

    private static final long serialVersionUID = 1L;

    @Schema(description = "页码 (从 1 开始，针对当前模块列表)", example = "1")
    private Integer pageNo;

    @Schema(description = "每页条数 / 明细上限条数", example = "20")
    private Integer pageSize;

    @Schema(description = "显式投影字段 ID 列表 (sys_module_field.id)。为 null 或空时默认投影当前模块全部可见字段")
    private List<Long> fields;

    @Schema(description = "专属于当前模块节点的过滤条件列表")
    private List<DynamicFilterItem> filters;

    @Schema(description = "专属于当前模块节点的排序规则列表")
    private List<DynamicSortItem> sorts;

    @Builder.Default
    @Schema(description = "是否在响应中返回树形表头契约 (默认为 true)", example = "true")
    private Boolean withHeader = true;
}
