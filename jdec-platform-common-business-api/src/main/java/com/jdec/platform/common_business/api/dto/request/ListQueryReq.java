package com.jdec.platform.common_business.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "带筛选条件的表头数据获取请求参数类,不带审批链的普通列表")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListQueryReq {

    @Schema(description = "菜单ID")
    private Long menuId;

    @Schema(description = "关联表的查询id，作为筛选条件")
    private Long id;

    @Schema(description = "每页条数")
    private Long pageSize;

    @Schema(description = "页码")
    private Long page;

    @Schema(description = "筛选项")
    private List<SearchItemReq> search;

    @Schema(description = "排序")
    private List<OrderItemReq> order;
}
