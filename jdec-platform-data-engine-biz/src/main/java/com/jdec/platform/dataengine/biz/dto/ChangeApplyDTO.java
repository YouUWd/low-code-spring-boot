package com.jdec.platform.dataengine.biz.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChangeApplyDTO {

    private String changeNo;

    private Long sourceId;

    private String modifyReason;
}
