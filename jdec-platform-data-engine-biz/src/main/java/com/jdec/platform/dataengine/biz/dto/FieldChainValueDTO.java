package com.jdec.platform.dataengine.biz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FieldChainValueDTO {

    @Schema(description = "字段特定配置值")
    private String fieldValue;

    @Schema(description = "字段特定配置值名称(fieldValue来自词云才有)")
    private String fieldValueDesc;

    @Schema(description = "关联审批链分类ID")
    private Long approvalChainTypeId;

    @Schema(description = "关联审批链分类名称")
    private String approvalChainTypeName;
}
