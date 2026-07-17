package com.jdec.platform.approval.api.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "审批请求")
public class ApprovalProcessReq {
    // 所属主体ID
    @Schema(description = "主体ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "主体ID不能为空")
    private Long subjectId;

    // "模块ID"
    @Schema(description = "模块ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模块ID不能为空")
    private Long moduleId;

    // 业务记录ID
    @Schema(description = "业务记录ID")
    private Long recordId;

    // 旧的业务记录ID
    @Schema(description = "变更前的业务记录ID")
    private Long OldRecordId;

    // 操作角色ID
    @Schema(description = "操作角色ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "操作角色ID不能为空")
    private Long roleId;

    // 登录用户ID
    @Schema(description = "登录用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "登录用户ID不能为空")
    private Long currentUserId;

    // 操作人ID
    @Schema(description = "操作用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "操作用户ID不能为空")
    private Long applicantId;

    // 交互动作
    @Schema(description = "交互动作(create/submit/change/cancel/edit/pass/reject)")
    private String button;

    // 驳回原因、取消原因
    @Schema(description = "字段变更记录、驳回原因、取消原因")
    private String reason;

    // 驳回到指定状态步骤
    @Schema(description = "驳回到指定状态步骤")
    private Integer rejectStep;

    // 新建业务时的编号前缀
    @Schema(description = "新建业务时的编号前缀")
    private String prefix;
}
