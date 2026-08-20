package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 状态树形节点响应 */
@Data
@Schema(description = "状态树形节点")
public class SysStatusTreeResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "父id")
    private Long pid;

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

    @Schema(description = "排序值，从0开始")
    private Integer sortOrder;

    @Schema(description = "子节点列表")
    private List<SysStatusTreeResp> children;
}
