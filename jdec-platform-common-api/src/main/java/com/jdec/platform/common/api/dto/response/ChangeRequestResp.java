package com.jdec.platform.common.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "变更申请详情响应模型 (包含模块标识、元数据与物理表业务数据、变更申请单的数据)")
public class ChangeRequestResp {
    @Schema(description = "详情数据")
    private DetailDataResp detailData;

    @Schema(description = "变更申请单的数据")
    private Map<String, Object> changeRequestData;
}
