package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 按钮精简响应（供前端按菜单查询使用，仅返回前端需要的字段） */
@Data
@Schema(description = "按钮精简响应（名称、类型、颜色相关）")
public class SysButtonSimpleResp {

    @Schema(description = "按钮名称")
    private String title;

    @Schema(description = "按钮类型")
    private String type;

    @Schema(description = "默认背景颜色")
    private String defaultBgColor;

    @Schema(description = "默认字体颜色")
    private String defaultFontColor;

    @Schema(description = "默认边框色")
    private String defaultBorder;

    @Schema(description = "悬浮背景颜色")
    private String levitateBgColor;

    @Schema(description = "悬浮字体颜色")
    private String levitateFontColor;

    @Schema(description = "悬浮边框色")
    private String levitateBorderColor;

    @Schema(description = "选中背景颜色")
    private String selectedBgColor;

    @Schema(description = "选中字体颜色")
    private String selectedFontColor;

    @Schema(description = "选中边框色")
    private String selectedBorderColor;
}
