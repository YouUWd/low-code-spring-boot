package com.jdec.platform.config.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块基础配置 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模块基础配置信息")
public class ModuleBasicDTO {

    @Schema(description = "模块ID", example = "101")
    private Long id;

    @Schema(description = "项目编号", example = "PRJ-001")
    private String projectNo;

    @Schema(description = "所属主体 ID", example = "10")
    private Long subjectId;

    @Schema(description = "模块唯一编码", example = "MOD-STUDENT")
    private String moduleCode;

    @Schema(description = "模块名称", example = "学生管理")
    private String moduleName;

    @Schema(description = "模块描述", example = "学生综合档案管理模块")
    private String moduleDesc;

    @Schema(description = "模块类别: 1-业务模块, 2-系统模块", example = "1")
    private Integer category;

    @Schema(description = "数据来源主体ID集合")
    private List<Long> sourceSubjects;

    @Schema(description = "父模块 ID (0 表示根模块)", example = "0")
    private Long parentId;

    @Schema(description = "排序顺序", example = "1")
    private Integer sortOrder;

    @Schema(description = "物理主表名", example = "student")
    private String primaryTable;

    @Schema(description = "物理字段字典列表 (sys_module_field)")
    private List<ModuleFieldDTO> fields;
}
