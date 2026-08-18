package com.jdec.platform.common_business.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "审批相关的操作")
public class ApprovalOperationReq {
    @Schema(description = "操作类型")
    private String btnType;

    @Schema(description = "驳回或取消原因")
    private String reason;

    @Schema(description = "驳回到指定状态步骤")
    private Integer rejectStep;

    @Schema(description = "是否自动审批")
    private Boolean autoApproval;
}
