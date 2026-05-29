package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 字段值配置响应（sys_field_value） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字段业务配置值响应")
public class SysFieldValueResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "字段配置ID")
    private Long fieldId;

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "模块名称")
    private String moduleName;

    @Schema(description = "字段特定配置值")
    private String fieldValue;

    @Schema(description = "字段特定配置值名称(fieldValue来自词云才有)")
    private String fieldValueDesc;

    @Schema(description = "关联审批链分类ID")
    private Long approvalChainTypeId;

    @Schema(description = "关联审批链分类名称")
    private String approvalChainTypeName;

    @Schema(description = "显示排序")
    private Integer sortOrder;

    @Schema(description = "是否启用: 0-禁用, 1-启用")
    private Integer enabled;
}
