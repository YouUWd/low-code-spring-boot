package com.jdec.platform.monitorlog.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "记录监控操作日志请求")
public class MonitorControlLogReq extends MonitorBaseLogReq {

    @Schema(description = "审批类型：0，自动审批。1，手动审批")
    private Integer approvalType = 1;

    @Schema(description = "动作名称")
    private String actionName;

    @Schema(description = "审批动作图标")
    private String actionPath;

    @Schema(description = "审批结果")
    private String actionTypeTitle;

    @Schema(description = "审批类型，前端根据类型决定审批结果字体颜色")
    private String actionType;
}
