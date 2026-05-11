package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 字段值配置保存请求 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字段业务配置值保存请求")
public class SysFieldValueSaveReq {

    @Schema(description = "主键ID（更新时必填）")
    private Long id;

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "模块名称")
    private String moduleName;

    @Schema(description = "字段特定配置值")
    private String fieldValue;

    @Schema(description = "关联审批链ID")
    private Long approvalChainTypeId;

    @Schema(description = "显示排序")
    private Integer sortOrder;

    @Schema(description = "是否启用: 0-禁用, 1-启用")
    private Integer enabled;
}
