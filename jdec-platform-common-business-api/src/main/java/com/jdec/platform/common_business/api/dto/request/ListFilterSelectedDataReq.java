package com.jdec.platform.common_business.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "去除其他筛选项中不在结果集的候选项")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListFilterSelectedDataReq {
    @Schema(description = "菜单ID")
    private Long menuId;

    @Schema(description = "关联表的查询id，作为筛选条件")
    private Long id;

    @Schema(description = "其他筛选项")
    private List<SearchItemReq> search;

    @Schema(description = "查询类别，1待办2已办 3全部(默认全部)")
    private Integer type;
}
