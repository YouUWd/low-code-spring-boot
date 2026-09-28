package com.jdec.platform.dataengine.biz.decoupleprocess.event;

import lombok.Data;
import org.springframework.context.ApplicationEvent;

@Data
public class ApprovalProcessEvent extends ApplicationEvent {

    private Long subjectId;

    private Long moduleId;

    private Long menuId;

    private Long approvalTypeId;

    private Long oldRecordId;

    private Long roleId;

    private Long currentUserId;

    private Long applicantId;

    private String button;

    private String reason;

    private Integer rejectStep;

    private String prefix;

    public ApprovalProcessEvent(
            Object source,
            Long subjectId,
            Long moduleId,
            Long menuId,
            Long approvalTypeId,
            Long oldRecordId,
            Long roleId,
            Long currentUserId,
            Long applicantId,
            String button,
            String reason,
            Integer rejectStep,
            String prefix) {
        super(source);
        this.subjectId = subjectId;
        this.moduleId = moduleId;
        this.menuId = menuId;
        this.approvalTypeId = approvalTypeId;
        this.oldRecordId = oldRecordId;
        this.roleId = roleId;
        this.currentUserId = currentUserId;
        this.applicantId = applicantId;
        this.button = button;
        this.reason = reason;
        this.rejectStep = rejectStep;
        this.prefix = prefix;
    }

    public ApprovalProcessEvent(Object source) {
        super(source);
    }
}
