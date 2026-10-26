package com.jdec.platform.dataengine.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "单据状态变更请求")
public class BusinessFormStatusReq {

    @Schema(description = "根模块ID")
    private Long moduleId;

    @Schema(description = "单据编号")
    private String businessNo;

    @Schema(description = "原始状态")
    private Integer originalStatus;

    @Schema(description = "目标状态")
    private Integer targetStatus;
}
