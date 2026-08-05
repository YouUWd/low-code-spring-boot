package com.jdec.platform.monitorlog.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "记录监控系统导航日志请求")
public class MonitorSystemNavLogReq extends MonitorBaseLogReq {

    @Schema(description = "导航URL")
    private String controlUrl;

    @Schema(description = "导航描述")
    private String controlDesc;
}
