package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 按钮样式审计展示对象
 *
 * <p>用于审批链配置审计日志中「按钮样式」列的展示，通过 {@code @AuditField(targetClass = ...)} 将 sys_button
 * 关联表记录按本类型字段集合转换后整体写入审计值。
 */
@Data
@Schema(description = "按钮样式审计展示对象")
public class ButtonStyleDTO {

    @Schema(description = "按钮名称")
    private String title;

    @Schema(description = "默认背景颜色")
    private String defaultBgColor;

    @Schema(description = "默认字体颜色")
    private String defaultFontColor;

    @Schema(description = "选中背景颜色")
    private String selectedBgColor;

    @Schema(description = "选中字体颜色")
    private String selectedFontColor;

    @Schema(description = "悬浮背景颜色")
    private String levitateBgColor;

    @Schema(description = "悬浮字体颜色")
    private String levitateFontColor;
}
