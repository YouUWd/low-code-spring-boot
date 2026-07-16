package com.jdec.platform.approval.api.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "审批数据查询请求")
public class ApprovalDataReq {
    // 所属主体ID
    @Schema(description = "主体ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "主体ID不能为空")
    private Long subjectId;

    // "模块ID"
    @Schema(description = "模块ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模块ID不能为空")
    private Long moduleId;

    // 业务记录ID
    @Schema(description = "业务记录ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "业务记录ID不能为空")
    private Long recordId;
}
