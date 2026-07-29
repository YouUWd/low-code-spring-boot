package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.config.api.enums.EnableEnum;
import com.jdec.platform.config.api.enums.ShowStatusEnum;
import com.jdec.platform.shared.audit.annotation.AuditField;
import com.jdec.platform.shared.audit.enums.FieldType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 按钮保存/编辑请求 */
@Data
@Schema(description = "按钮保存/编辑请求")
public class SysButtonSaveReq {

    @Schema(description = "主键ID，为空时新增")
    private Long id;

    @AuditField(name = "按钮名称", uniqueIdentifier = true)
    @Schema(description = "按钮名称")
    private String title;

    @Schema(description = "别名")
    private String alias;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "默认背景颜色")
    private String defaultBgColor;

    @Schema(description = "默认字体颜色")
    private String defaultFontColor;

    @Schema(description = "悬浮背景颜色")
    private String levitateBgColor;

    @Schema(description = "悬浮字体颜色")
    private String levitateFontColor;

    @Schema(description = "选中背景颜色")
    private String selectedBgColor;

    @Schema(description = "选中字体颜色")
    private String selectedFontColor;

    @Schema(description = "日志对应图标")
    private String icon;

    @Schema(description = "日志对应状态")
    private String logName;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "1显示 0隐藏")
    @AuditField(name = "是否展示", type = FieldType.ENUM, enumClass = ShowStatusEnum.class)
    private Integer showed;

    @AuditField(name = "是否启用", type = FieldType.ENUM, enumClass = EnableEnum.class)
    @Schema(description = "是否启用(1:启用。0：不启用)")
    private Integer enabled;
}
