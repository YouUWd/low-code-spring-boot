package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 审批链分类下拉选项 */
@Data
@Schema(description = "审批链分类下拉选项")
public class ApprovalChainTypeOptionResp {

    @Schema(description = "审批链分类ID")
    private Long id;

    @Schema(description = "审批链分类名称")
    private String title;

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "是否默认")
    private Integer defaultFlag;

    @Schema(description = "是否启用")
    private Integer enabled;
}
