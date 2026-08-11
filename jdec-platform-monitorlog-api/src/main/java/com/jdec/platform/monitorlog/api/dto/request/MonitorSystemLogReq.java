package com.jdec.platform.monitorlog.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "记录监控系统控制日志请求")
public class MonitorSystemLogReq extends MonitorBaseLogReq {

    @Schema(
            description =
                    "日志类型(loginin/loginout/changerole/mockuser/mockchangerole/resetuser/changesubject)，"
                            + "传入后服务端自动生成logContent",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String logType;
}
