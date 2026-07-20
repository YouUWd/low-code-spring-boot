package com.jdec.platform.common_business.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "带筛选条件的表头数据获取请求参数类,不带审批链的普通列表")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalListQueryReq extends ListQueryReq {
    @Schema(description = "查询类别，1待办2已办 3全部(默认全部)")
    private Integer type;
}
