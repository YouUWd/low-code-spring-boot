package com.jdec.platform.common.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 基于菜单的动态数据列表全量查询请求模型 (MenuListReq)
 *
 * <p>面向通过物理表名与列名进行数据查询的使用场景。 不包含分页参数与审批流类型，直接返回符合条件的最新启用版本数据记录列表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "基于菜单的动态数据列表全量查询请求参数")
public class MenuListReq implements Serializable {

    @Schema(description = "物理表投影规格列表 (为 null 或空时默认查当前模块及其关联子表的全部字段)")
    private List<MenuTableSpec> tables;

    @Schema(description = "基于物理表与列名的平铺过滤条件列表 (默认以平级 AND 组合)")
    private List<FlatFilterItem> filters;

    @Schema(description = "基于物理表与列名的平铺排序规则列表")
    private List<FlatSortItem> sorts;
}
