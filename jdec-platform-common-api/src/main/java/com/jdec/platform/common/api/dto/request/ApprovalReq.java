package com.jdec.platform.common.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "审批请求")
public class ApprovalReq { // 审批事件消息
    // 业务记录ID
    @Schema(description = "业务记录ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long recordId;

    // 业务单据编号
    @Schema(description = "业务单据编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String businessNumber;

    // 交互动作(create/submit/change/cancel/edit/pass/reject)
    @Schema(
            description = "交互动作(create/submit/change/cancel/edit/pass/reject)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String button;

    // 驳回原因、取消原因
    @Schema(description = "驳回原因、取消原因")
    private Object reason;

    // 驳回到指定状态步骤
    @Schema(description = "驳回到指定状态步骤")
    private Integer rejectStep;

    // 审批分类ID
    @Schema(description = "审批分类ID")
    private Long approvalTypeId;
}
