package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 审批链级联下拉响应 */
@Data
@Schema(description = "审批链级联下拉响应")
public class ApprovalChainCascadeResp {

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "模块名称")
    private String moduleName;

    @Schema(description = "审批链分类列表")
    private List<ApprovalChainTypeOption> chainTypes;

    /** 审批链分类选项 */
    @Data
    @Schema(description = "审批链分类选项")
    public static class ApprovalChainTypeOption {

        @Schema(description = "审批链分类ID")
        private Long id;

        @Schema(description = "审批链分类名称")
        private String title;

        @Schema(description = "是否默认")
        private Integer defaultFlag;
    }
}
