package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 审批链分类分页响应 */
@Data
@Schema(description = "审批链分类分页响应")
public class ApprovalChainTypePageResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "分类名称")
    private String title;

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "模块名称")
    private String moduleName;

    @Schema(description = "是否默认审批链分类（1：是，0：否）")
    private Integer defaultFlag;

    @Schema(description = "是否启用(1:启用, 0：不启用)")
    private Integer enabled;
}
