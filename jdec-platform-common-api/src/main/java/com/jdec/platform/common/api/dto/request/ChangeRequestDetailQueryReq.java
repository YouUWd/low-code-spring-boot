package com.jdec.platform.common.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "基于菜单的变更申请详情数据查询请求")
public class ChangeRequestDetailQueryReq extends MenuDetailQueryReq {

    @Schema(description = "变更申请ID", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long changeRequestId;
}
