package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单表轻量查询请求 (SingleTableQueryReq)
 *
 * <p>面向单张物理表的原子查询契约，不依赖模块树及跨表编译，提供极致轻量的单表投影、过滤与排序能力。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "单表轻量查询请求")
public class SingleTableQueryReq implements Serializable {

    @Schema(
            description = "物理表名 (必填)",
            example = "info",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String tableName;

    @Schema(description = "指定需要投影的列名列表 (缺省或为空时默认查询全部或仅主键)", example = "[\"id\", \"business_no\"]")
    private List<String> columns;

    @Schema(description = "平铺过滤条件列表 (基于列名的条件，支持 EQ, IN, LIKE, GT, BETWEEN 等)")
    private List<FilterItem> filters;

    @Schema(description = "排序规则列表 (基于列名排序)")
    private List<SortItem> sorts;

    @Schema(description = "限制返回的最大条数 (可选，缺省无限制)", example = "100")
    private Integer limit;

    @Schema(description = "是否启用 DISTINCT 去重投影 (可选，缺省为 false)", example = "true")
    private Boolean distinct;

    public static SingleTableQueryReq of(String tableName) {
        return SingleTableQueryReq.builder().tableName(tableName).build();
    }

    public static SingleTableQueryReq of(String tableName, List<String> columns) {
        return SingleTableQueryReq.builder().tableName(tableName).columns(columns).build();
    }

    public static SingleTableQueryReq of(String tableName, String... columns) {
        return SingleTableQueryReq.builder()
                .tableName(tableName)
                .columns(columns != null ? java.util.Arrays.asList(columns) : null)
                .build();
    }

    public SingleTableQueryReq addFilter(FilterItem filter) {
        if (filter != null) {
            if (this.filters == null) {
                this.filters = new java.util.ArrayList<>();
            }
            this.filters.add(filter);
        }
        return this;
    }

    public SingleTableQueryReq addSort(SortItem sort) {
        if (sort != null) {
            if (this.sorts == null) {
                this.sorts = new java.util.ArrayList<>();
            }
            this.sorts.add(sort);
        }
        return this;
    }
}
