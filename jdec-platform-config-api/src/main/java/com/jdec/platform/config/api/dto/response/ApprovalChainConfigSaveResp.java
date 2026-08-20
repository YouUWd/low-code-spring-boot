package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 审批链配置保存响应 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "审批链配置保存响应")
public class ApprovalChainConfigSaveResp {

    @Schema(description = "配置ID")
    private Long id;

    @Schema(description = "操作结果 1:成功")
    private Integer result;
}
