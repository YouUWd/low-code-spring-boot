package com.jdec.platform.data.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jdec.platform.data.api.model.predicate.ComparisonPredicate;
import com.jdec.platform.data.api.model.predicate.CompoundPredicate;
import com.jdec.platform.data.api.model.predicate.Predicate;
import io.swagger.v3.oas.annotations.media.Schema;
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
 * <p>基于自相似业务视图树 (View Tree) 与统一 table.column 物理表/列投影规约：
 *
 * <ul>
 *   <li>主视图 (Root View)：[大视图] 主表 + 垂直伴生表 (1:1) + 上游字典表 (N:1)
 *   <li>子视图 (1:N)：[从表视图] 主表 + 伴生表，通过 children 递归嵌套，完全隔离作用域
 *   <li>虚拟模块 (0:0)：纯逻辑分组与下钻物理子模块透传
 * </ul>
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "业务数据动态查询基础请求模型 (自相似 View 树)")
public class QueryReq implements Serializable {

    @Schema(description = "业务视图编码 (如 COMPANY, DIRECTORS, LEGAL_REP)", example = "COMPANY")
    @JsonProperty("view")
    private String view;

    @Schema(
            description =
                    "统一投影列名列表 (全链路统一使用 table.column 格式，如 info.id, info.company_name, basic_info.currency)")
    @JsonProperty("columns")
    private List<String> columns;

    @Schema(description = "基于 MySQL 标准布尔抽象语法树的过滤条件")
    @JsonProperty("filter")
    @JsonAlias({"filter", "filters"})
    @JsonDeserialize(using = FilterDeserializer.class)
    private Predicate filter;

    @Schema(description = "基于物理表与列名的排序规则列表")
    @JsonProperty("sort")
    @JsonAlias({"sort", "sorts"})
    private List<SortItem> sort;

    @Schema(description = "级联子视图查询规格列表 (自相似 View 树递归嵌套)")
    @JsonProperty("children")
    private List<QueryReq> children;

    // ==================== 便捷链式方法 ====================

    /** 追加投影列 (table.column 格式) */
    @SuppressWarnings("unchecked")
    public <T extends QueryReq> T column(String tableDotColumn) {
        if (tableDotColumn != null && !tableDotColumn.isBlank()) {
            if (this.columns == null) {
                this.columns = new ArrayList<>();
            } else if (!(this.columns instanceof ArrayList)) {
                this.columns = new ArrayList<>(this.columns);
            }
            this.columns.add(tableDotColumn.trim());
        }
        return (T) this;
    }

    /** 批量追加投影列 */
    @SuppressWarnings("unchecked")
    public <T extends QueryReq> T columns(String... cols) {
        if (cols != null) {
            for (String c : cols) {
                column(c);
            }
        }
        return (T) this;
    }

    /** 批量追加投影列 */
    @SuppressWarnings("unchecked")
    public <T extends QueryReq> T columns(List<String> cols) {
        if (cols != null) {
            for (String c : cols) {
                column(c);
            }
        }
        return (T) this;
    }

    /** 设置或追加过滤条件 (自动与已有条件 AND 组合) */
    @SuppressWarnings("unchecked")
    public <T extends QueryReq> T filter(Predicate p) {
        if (p != null) {
            if (this.filter == null) {
                this.filter = p;
            } else if (this.filter instanceof CompoundPredicate cp
                    && "AND".equalsIgnoreCase(cp.getOperator())) {
                List<Predicate> list = new ArrayList<>(cp.getPredicates());
                list.add(p);
                this.filter = CompoundPredicate.and(list);
            } else {
                this.filter = CompoundPredicate.and(this.filter, p);
            }
        }
        return (T) this;
    }

    /** 追加比较过滤条件 */
    public <T extends QueryReq> T filter(String field, String op, Object value) {
        return filter(ComparisonPredicate.of(field, op, value));
    }

    /** 追加排序规则 */
    @SuppressWarnings("unchecked")
    public <T extends QueryReq> T sort(SortItem item) {
        if (item != null) {
            if (this.sort == null) {
                this.sort = new ArrayList<>();
            } else if (!(this.sort instanceof ArrayList)) {
                this.sort = new ArrayList<>(this.sort);
            }
            this.sort.add(item);
        }
        return (T) this;
    }

    public <T extends QueryReq> T sort(String field, String direction) {
        return sort(SortItem.builder().field(field).direction(direction).build());
    }

    /** 追加子视图查询规格 */
    @SuppressWarnings("unchecked")
    public <T extends QueryReq> T child(QueryReq childReq) {
        if (childReq != null) {
            if (this.children == null) {
                this.children = new ArrayList<>();
            } else if (!(this.children instanceof ArrayList)) {
                this.children = new ArrayList<>(this.children);
            }
            this.children.add(childReq);
        }
        return (T) this;
    }

    // ==================== 兼容别名方法 (方便旧代码平滑调用) ====================

    public Predicate getFilters() {
        return filter;
    }

    public void setFilters(Predicate f) {
        this.filter = f;
    }

    public List<SortItem> getSorts() {
        return sort;
    }

    public void setSorts(List<SortItem> s) {
        this.sort = s;
    }
}
