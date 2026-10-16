package com.jdec.platform.approval.api.dto;

import lombok.Data;

@Data
public class ApprpvalIdButton {
    // 业务记录ID
    private Long recordId;
    // 业务单据编号
    private String businessNo;
    // 按钮标识
    private String buttonKey;
    // 按钮名称
    private String buttonName;
}
