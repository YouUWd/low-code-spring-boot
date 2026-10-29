package com.jdec.platform.approval.api.resp;

import lombok.Data;

@Data
public class ApprovalBusinessNoResp {
    String businessNumber;
    Integer approvalType; // 1-待办；2-已办；3-其他；
}
