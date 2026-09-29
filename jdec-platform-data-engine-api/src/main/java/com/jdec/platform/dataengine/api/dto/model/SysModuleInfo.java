package com.jdec.platform.dataengine.api.dto.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块信息 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模块信息")
public class SysModuleInfo {

    @Schema(description = "模块ID", example = "1")
    private Long moduleId;

    @Schema(description = "父模块ID", example = "1")
    private Long parentId;

    @Schema(description = "模块标识", example = "1")
    private String moduleCode;

    @Schema(description = "模块名称", example = "1")
    private String moduleName;

    @Schema(description = "模块描述", example = "1")
    private String moduleDesc;

    @Schema(description = "模块主表名称", example = "1")
    private String primaryTable;

    @Schema(description = "模块字段", example = "1")
    @JsonIgnore
    private List<ModuleFieldDTO> fieldList;

    @Schema(description = "模块表关系", example = "1")
    @JsonIgnore
    private List<TableRelationDTO> tableRelations;

    @Schema(description = "子模块信息", example = "1")
    @JsonIgnore
    private List<ModuleNodeDTO> moduleNodes;

    @JsonIgnore private List<SysModuleInfo> children;

    // 子表
    private Set<String> subTables;

    private Set<String> writeFields;

    private Set<String> updateFields;

    private Set<String> readFields;

    private Set<String> allFields;
}
