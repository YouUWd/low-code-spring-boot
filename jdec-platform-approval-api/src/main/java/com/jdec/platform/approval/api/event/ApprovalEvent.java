package com.jdec.platform.approval.api.event;

import lombok.Data;

@Data
public class ApprovalEvent { // 审批事件消息
    // 所属主体ID
    private Long subjectId;

    // 模块ID
    private Long moduleId;

    // 菜单ID
    private Long menuId;

    // 审批分类ID
    private Long approvalTypeId;

    // 业务记录ID
    private Long recordId;

    // 业务单据编号
    private String businessNumber;

    // 业务单据版本号
    private Integer versionNo;

    // 操作角色ID
    private Long roleId;

    // 登录用户ID
    private Long currentUserId;

    // 操作人ID
    private Long applicantId;

    // 交互动作(create/submit/change/cancel/edit/pass/reject)
    private String button;

    // 驳回原因、取消原因
    private String reason;

    // 驳回到指定状态步骤
    private Integer rejectStep;
}
