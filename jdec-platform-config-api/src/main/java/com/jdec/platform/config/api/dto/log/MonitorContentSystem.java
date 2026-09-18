package com.jdec.platform.config.api.dto.log;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 监控日志内容（系统日志）：在基类共用字段之上追加菜单参数 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "监控日志内容（系统日志）")
public class MonitorContentSystem extends MonitorContentBase {

    @Schema(description = "菜单参数", example = "external-object/list")
    private String menuParam;
}
