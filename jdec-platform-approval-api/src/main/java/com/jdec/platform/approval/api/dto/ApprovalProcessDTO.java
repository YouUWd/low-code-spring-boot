package com.jdec.platform.approval.api.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ApprovalProcessDTO {
    // 模块ID
    private Long moduleId;

    // 菜单ID
    private Long menuId;

    // 审批分类ID
    private Long approvalTypeId;

    // 业务记录ID
    private Long recordId;

    // 旧的业务记录ID
    private Long OldRecordId;

    // 业务申请人
    private Long createdBy;

    // 已审批人
    private String approvalUids;

    // 业务单据编号
    private String businessNumber;

    // 业务单据版本号
    private Integer versionNo;

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
    private Object reason;

    // 驳回到指定状态步骤
    private Integer rejectStep;

    // 操作时间
    private LocalDateTime createdDate;
}
