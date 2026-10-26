package com.jdec.platform.common.api.dto.log;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 监控日志内容（操作日志）：在基类共用字段之上追加操作结果、字体颜色与菜单参数 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "监控日志内容（操作日志）")
public class MonitorContentOperation extends MonitorContentBase {

    /** 字体颜色默认值：调用方未传时使用 */
    public static final String DEFAULT_FONT_COLOR = "#000000";

    @Schema(description = "操作结果", example = "成功")
    private String result;

    @Schema(description = "字体颜色，调用方未传时默认 #000000", example = "#00A870")
    private String fontColor = DEFAULT_FONT_COLOR;

    @Schema(description = "菜单参数", example = "external-object/list")
    private String menuParam;
}
