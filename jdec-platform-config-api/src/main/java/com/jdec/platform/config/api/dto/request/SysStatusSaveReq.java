package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 状态保存/编辑请求
 */
@Data
@Schema(description = "状态保存/编辑请求")
public class SysStatusSaveReq {

    @Schema(description = "主键ID，为空时新增")
    private Long id;

    @Schema(description = "父id，顶层为0")
    private Long pid;

    @Schema(description = "所属主体")
    private Long subjectId;

    @Schema(description = "状态名称")
    private String title;

    @Schema(description = "状态值")
    private Integer statusValue;

    @Schema(description = "状态背景色")
    private String statusBackground;

    @Schema(description = "状态字体颜色")
    private String statusFontColor;

    @Schema(description = "是否启用(1:启用。0：不启用)")
    private Integer enabled;

    @Schema(description = "审批是否显示(1:显示。0：不显示)")
    private Integer approvalShowFlag;
}
