package com.jdec.platform.common_business.api.dto.common;

import com.jdec.platform.common_business.api.dto.request.OrderItemReq;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "列表动态查询参数")
public class ListQueryParam {

    @Schema(description = "主体ID")
    private Long subjectId;

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "和其他表的关联键的筛选ID")
    private Long id;

    @Schema(description = "模块相关配置(列配置、权限节点等)")
    private ModuleFilterInfo moduleFilterInfo;

    @Schema(description = "按钮状态判断需要跳过的模块ID集合")
    private List<Long> moduleSkip;

    @Schema(description = "是否是列头筛选查询")
    private Boolean columnFilter;

    @Schema(description = "已筛选过的主表数据集合")
    List<Object> primaryTableDataList;

    @Schema(description = "排序相关")
    List<OrderItemReq> orderItems;

    @Schema(description = "带审批链的查询，查询种类 单据类型（待办=1，已办=2，全部=3）")
    private Integer type;
}
