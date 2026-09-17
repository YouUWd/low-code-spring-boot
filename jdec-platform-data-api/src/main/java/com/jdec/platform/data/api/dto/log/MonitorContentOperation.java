package com.jdec.platform.data.api.dto.log;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 监控日志内容（操作日志）：在基类共用字段之上追加操作结果与字体颜色 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "监控日志内容（操作日志）")
public class MonitorContentOperation extends MonitorContentBase {

    @Schema(description = "操作结果", example = "成功")
    private String result;

    @Schema(description = "字体颜色", example = "#00A870")
    private String fontColor;
}
