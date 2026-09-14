package com.jdec.platform.dataengine.biz.config.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块树节点信息 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模块树节点信息")
public class ModuleNodeDTO {

    @Schema(description = "模块ID", example = "1")
    private Long id;

    @Schema(description = "父模块ID", example = "1")
    private Long parentId;

    @Schema(description = "模块标识", example = "1")
    private String moduleCode;

    @Schema(description = "模块名称", example = "1")
    private String moduleName;

    @Schema(description = "模块主表名称", example = "1")
    private String primaryTable;

    /** 排序顺序 */
    private Integer sortOrder;
}
