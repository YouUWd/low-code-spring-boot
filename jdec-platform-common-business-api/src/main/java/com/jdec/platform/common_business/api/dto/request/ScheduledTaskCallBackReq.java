package com.jdec.platform.common_business.api.dto.request;

import lombok.Data;

@Data
public class ScheduledTaskCallBackReq {

    private Long moduleId;

    private Long recordId;

    private Long menuId;
}
