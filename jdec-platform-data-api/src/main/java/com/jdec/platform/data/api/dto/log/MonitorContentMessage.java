package com.jdec.platform.data.api.dto.log;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 监控日志内容（消息日志）：在基类共用字段之上追加消息正文 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "监控日志内容（消息日志）")
public class MonitorContentMessage extends MonitorContentBase {

    @Schema(description = "日志内容（消息正文）", example = "您有一条待办审批")
    private String content;
}
