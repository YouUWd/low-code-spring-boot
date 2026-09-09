package com.jdec.platform.dataengine.biz.vo;

import lombok.Builder;
import lombok.Data;

/** 审批链步骤信息模型类 */
@Data
@Builder
public class ApprovalStepInfo {

    private String statusBackground;

    private Integer isOperate;

    private String statusFontColor;

    private Integer statusValue;

    private String approvalRule;

    private Integer isHighlight;

    private Boolean isCollaboration;

    private Long id;

    private Integer canDo;

    private String title;

    private Boolean isApproved;

    private Integer order;
}
