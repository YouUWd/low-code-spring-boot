package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.config.api.enums.EnableEnum;
import com.jdec.platform.config.api.enums.YesNoEnum;
import com.jdec.platform.shared.audit.annotation.AuditField;
import com.jdec.platform.shared.audit.enums.FieldType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 状态保存/编辑请求 */
@Data
@Schema(description = "状态保存/编辑请求")
public class SysStatusSaveReq {

    @AuditField(name = "主键ID", ignore = true)
    @Schema(description = "主键ID，为空时新增")
    private Long id;

    @AuditField(
            name = "父状态",
            type = FieldType.RELATION,
            target = "sys_status",
            idField = "id",
            nameField = "title")
    @Schema(description = "父id，顶层为0")
    private Long pid;

    @AuditField(name = "状态名称", uniqueIdentifier = true)
    @Schema(description = "状态名称")
    private String title;

    @AuditField(name = "状态值")
    @Schema(description = "状态值")
    private Integer statusValue;

    @AuditField(name = "状态背景色")
    @Schema(description = "状态背景色")
    private String statusBackground;

    @AuditField(name = "状态字体颜色")
    @Schema(description = "状态字体颜色")
    private String statusFontColor;

    @AuditField(name = "是否审批链显示", type = FieldType.ENUM, enumClass = YesNoEnum.class)
    @Schema(description = "审批是否显示(1:是0：否)")
    private Integer approvalShowFlag;

    @AuditField(name = "状态", type = FieldType.ENUM, enumClass = EnableEnum.class)
    @Schema(description = "是否启用(1:启用。0：不启用)")
    private Integer enabled;

    @AuditField(name = "排序值", ignore = true)
    @Schema(description = "排序值，从0开始")
    private Integer sortOrder;
}
