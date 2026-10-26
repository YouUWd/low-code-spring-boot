package com.jdec.platform.config.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 子模块树节点 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "子模块树节点信息")
public class ModuleNodeDTO {

    @Schema(description = "模块ID", example = "103")
    private Long id;

    @Schema(description = "父模块ID", example = "101")
    private Long parentId;

    @Schema(description = "项目编号", example = "PRJ001")
    private String projectNo;

    @Schema(description = "所属主体ID", example = "1")
    private Long subjectId;

    @Schema(description = "模块编码", example = "MOD-STUDENT-COURSE")
    private String moduleCode;

    @Schema(description = "模块名称", example = "选课与成绩管理")
    private String moduleName;

    @Schema(description = "物理主表名", example = "student_course")
    private String primaryTable;

    @Schema(description = "排序顺序", example = "1")
    private Integer sortOrder;
}
