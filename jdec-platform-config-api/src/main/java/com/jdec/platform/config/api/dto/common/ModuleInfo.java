package com.jdec.platform.config.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统模块信息 (SysModuleInfo)
 *
 * <p>全平台统一的核心原子元数据载体，驱动动态查询构建、自相似树编译、读写同构保存与详情响应。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "系统模块信息")
public class ModuleInfo {

    @Schema(description = "模块ID", example = "101")
    private Long moduleId;

    @Schema(description = "父模块ID (0 表示根模块)", example = "0")
    private Long parentId;

    @Schema(description = "项目编号", example = "PRJ001")
    private String projectNo;

    @Schema(description = "所属主体ID", example = "1")
    private Long subjectId;

    @Schema(description = "模块编码", example = "MOD-STUDENT")
    private String moduleCode;

    @Schema(description = "模块名称", example = "学生管理")
    private String moduleName;

    @Schema(description = "模块描述", example = "学生综合档案管理模块")
    private String moduleDesc;

    @Schema(description = "物理主表名", example = "student")
    private String primaryTable;

    @Schema(description = "物理字段字典列表 (sys_module_field)")
    private List<ModuleFieldDTO> fields;
}
