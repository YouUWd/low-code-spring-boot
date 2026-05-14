package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 审批链分类保存请求 */
@Data
@Schema(description = "审批链分类保存请求")
public class ApprovalChainTypeSaveReq {

    @Schema(description = "主键ID（编辑时必填，新增时为空）")
    private Long id;

    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "分类名称不能为空")
    private String title;

    @Schema(description = "模块ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "模块ID不能为空")
    private Long moduleId;

    @Schema(description = "是否默认审批链分类（1：是，0：否）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "是否默认不能为空")
    private Integer defaultFlag;

    @Schema(description = "是否启用(1:启用, 0：不启用)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "是否启用不能为空")
    private Integer enabled;
}
