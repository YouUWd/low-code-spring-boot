package com.jdec.platform.approval.api.resp;

import lombok.Data;

@Data
public class ApprovalListResp {
    /** 业务记录ID */
    private Long recordId;

    /** 单据编号 */
    private String businessNumber;

    /** 当前单据版本号 */
    private Integer versionNo;

    /** 状态名称 */
    private String statusTitle;

    /** 状态背景色 */
    private String statusBackground;

    /** 状态字体颜色 */
    private String statusFontColor;

    /** 当前审批流状态 */
    private Integer statusValue;

    /** 审批类型 */
    private Integer approvalType;

    /** 按钮标题 */
    private String listButton;
}
