package com.jdec.platform.monitorlog.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "记录监控消息日志请求")
public class MonitorMessageLogReq extends MonitorBaseLogReq {

    @Schema(description = "审批类型：0，自动审批。1，手动审批")
    private Integer approvalType = 1;
}
