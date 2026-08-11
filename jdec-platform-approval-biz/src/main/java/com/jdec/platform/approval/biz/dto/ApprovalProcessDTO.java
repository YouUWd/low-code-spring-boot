package com.jdec.platform.approval.biz.dto;

import lombok.Data;

@Data
public class ApprovalProcessDTO {
    // 模块ID
    private Long moduleId;

    // 菜单ID
    private Long menuId;

    // 审批分类ID
    private Long approvalTypeId;

    // 真实业务ID
    private Long businessId;

    // 业务记录ID
    private Long recordId;

    // 旧的业务记录ID
    private Long OldRecordId;

    // 审批流编号
    private String serialNumber;

    // 审批流版本号
    private Integer serialNo;

    // 所属主体ID
    private Long subjectId;

    // 操作人ID
    private Long applicantId;

    // 交互动作
    private String button;

    // 操作角色
    private Long roleId;

    // 当前用户ID
    private Long currentUserId;

    // 当前节点
    private Integer currentStep;

    // 驳回原因、取消原因
    private String reason;

    // 驳回到指定状态步骤
    private Integer rejectStep;

    // 新建业务时的编号前缀
    private String prefix;
}
