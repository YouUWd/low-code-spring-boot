package com.jdec.platform.data.api.dto.log;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 监控日志审批信息（操作日志）：在基类共用字段之上追加审批类型 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "监控日志审批信息（操作日志）")
public class MonitorApproveOperation extends MonitorApproveBase {

    @Schema(description = "审批类型：0-自动审批，1-手动审批", example = "1")
    private Integer type;
}
