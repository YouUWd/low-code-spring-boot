package com.jdec.platform.dataengine.biz.dto.context;

import com.jdec.platform.dataengine.api.dto.request.OrderItemReq;
import com.jdec.platform.dataengine.api.dto.request.SearchItemReq;
import com.jdec.platform.dataengine.biz.dto.BasicModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
public class ListQueryContext extends BasicModel {
    @Schema(description = "筛选项")
    private List<SearchItemReq> search;

    @Schema(description = "查询类别，1待办2已办 3全部(默认全部) 4非审批链")
    private Integer type;

    @Schema(description = "排序")
    private List<OrderItemReq> order;

    @Schema(description = "模块 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "128")
    private Long moduleId;

    @Schema(description = "菜单 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "55")
    private Long menuId;

    @Schema(description = "页码 (从 1 开始)", example = "1")
    @Builder.Default
    private int pageIndex = 1;

    @Schema(description = "每页条数", example = "20")
    @Builder.Default
    private int pageSize = 20;

    @Schema(description = "详情中的列表查询关联的外键筛选ID值")
    private Long relateSearchId;
}
