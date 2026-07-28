package com.jdec.platform.common_business.api.dto.request;

import com.jdec.platform.common_business.api.dto.common.SelectedItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "带筛选条件的表头下拉框数据获取请求参数类")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListSelectQueryReq {
    @Schema(description = "菜单ID")
    private Long menuId;

    @Schema(description = "关联表的查询id，作为筛选条件")
    private Long id;

    @Schema(description = "其他筛选项")
    private List<SearchItemReq> search;

    @Schema(description = "待获取数据的筛选项")
    private List<SelectedItem> selectedItem;

    @Schema(description = "查询类别，1待办2已办 3全部(默认全部)")
    private Integer type;
}
