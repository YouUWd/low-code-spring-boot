package com.jdec.platform.approval.api.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "查询审批列表请求")
public class ApprovalListReq {
    // 单据类型
    @Schema(description = "单据类型（待办=1，已办=2，全部=3）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "单据类型不能为空")
    private Integer approvalType;

    // 所属主体ID
    @Schema(description = "主体ID，多主体查询", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "主体ID不能为空")
    private List<Long> subjectIdList;

    // "模块ID"
    @Schema(description = "模块ID，多主体查询", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模块ID不能为空")
    private List<Long> moduleIdList;

    // 业务记录ID
    @Schema(description = "业务记录ID列表")
    private List<Long> recordIdList;

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
}
