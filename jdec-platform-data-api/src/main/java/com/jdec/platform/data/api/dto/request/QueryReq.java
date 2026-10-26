package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 业务数据动态查询基础请求模型 (QueryReq)
 *
 * <p>基于业务聚合树 (Module Tree) 与物理表/列投影规约：
 *
 * <ul>
 *   <li>根模块 (Root Module)：[大视图] 主表 + 垂直伴生表 (1:1) + 上游字典表 (N:1)
 *   <li>子模块 (1:N)：[从表视图] 主表 + 伴生表
 *   <li>虚拟模块 (0:0)：纯逻辑分组与下钻物理子模块透传
 * </ul>
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "业务数据动态查询基础请求模型")
public class QueryReq implements Serializable {

    @NotNull(message = "查询主模块 ID 不能为空")
    @Schema(description = "根主模块 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    private Long moduleId;

    @Schema(
            description =
                    "物理表投影规格列表 (每个 TableSpec 精准绑定一张物理表与该表的列名列表)。为 null 或空时默认查模块及子模块所有关联表的全部字段")
    private List<TableSpec> tables;

    @Schema(description = "基于物理表与列名的过滤条件列表")
    private List<FilterItem> filters;

    @Schema(description = "基于物理表与列名的排序规则列表")
    private List<SortItem> sorts;

    /** 追加投影表规格 */
    @SuppressWarnings("unchecked")
    public <T extends QueryReq> T table(TableSpec spec) {
        if (spec != null) {
            if (this.tables == null) {
                this.tables = new ArrayList<>();
            } else if (!(this.tables instanceof ArrayList)) {
                this.tables = new ArrayList<>(this.tables);
            }
            this.tables.add(spec);
        }
        return (T) this;
    }

    /** 追加过滤条件 */
    @SuppressWarnings("unchecked")
    public <T extends QueryReq> T filter(FilterItem filter) {
        if (filter != null) {
            if (this.filters == null) {
                this.filters = new ArrayList<>();
            } else if (!(this.filters instanceof ArrayList)) {
                this.filters = new ArrayList<>(this.filters);
            }
            this.filters.add(filter);
        }
        return (T) this;
    }

    /** 追加排序规则 */
    @SuppressWarnings("unchecked")
    public <T extends QueryReq> T sort(SortItem sort) {
        if (sort != null) {
            if (this.sorts == null) {
                this.sorts = new ArrayList<>();
            } else if (!(this.sorts instanceof ArrayList)) {
                this.sorts = new ArrayList<>(this.sorts);
            }
            this.sorts.add(sort);
        }
        return (T) this;
    }
}
