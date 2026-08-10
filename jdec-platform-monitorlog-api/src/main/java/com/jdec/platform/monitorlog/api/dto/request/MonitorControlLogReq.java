package com.jdec.platform.monitorlog.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "记录监控操作日志请求")
public class MonitorControlLogReq extends MonitorBaseLogReq {

    @Schema(description = "动作名称")
    private String actionName;

    @Schema(description = "审批动作图标")
    private String actionPath;

    @Schema(description = "审批结果")
    private String actionTypeTitle;

    @Schema(description = "审批动作类型")
    private String actionType;
}
