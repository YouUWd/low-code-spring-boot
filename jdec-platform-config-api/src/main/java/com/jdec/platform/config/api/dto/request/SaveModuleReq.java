package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.config.api.dto.common.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 保存模块完整信息请求 DTO 包含模块基本信息、关联表、字段配置、表头配置、状态机 */
@Data
@Schema(description = "保存模块完整信息请求")
public class SaveModuleReq {

    /** 模块基本信息 */
    @Schema(description = "模块基本信息", requiredMode = Schema.RequiredMode.REQUIRED)
    private SaveSysModuleReq module;

    /** 模块关联表列表 */
    @Schema(description = "模块关联表列表", example = "[]")
    private List<ModuleTableDTO> moduleTables;

    /** 模块物理字段配置列表 */
    @Schema(description = "模块物理字段配置列表", example = "[]")
    private List<ModuleSimpleFieldDTO> simpleFields;

    /** 列表表头配置列表 */
    @Schema(description = "列表表头配置列表", example = "[]")
    private List<ModuleTableHeaderDTO> moduleHeaders;

    /** 模块状态配置列表 */
    @Schema(description = "模块状态配置列表", example = "[]")
    private List<ModuleStatusDTO> moduleStatuses;

    /** 保存模块基本信息请求 DTO 用于创建或编辑模块的基本信息 */
    @Data
    @Schema(description = "保存模块基本信息请求")
    public static class SaveSysModuleReq {

        /** 模块 ID（编辑时使用，创建时为空） */
        @Schema(description = "模块ID（编辑时使用，创建时为空）", example = "1")
        private Long id;

        /** 模块唯一编码，如 MOD-STUDENT-FULL */
        @Schema(
                description = "模块唯一编码",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "MOD-STUDENT-FULL")
        private String moduleCode;

        /** 模块名称 */
        @Schema(description = "模块名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "学生管理")
        private String moduleName;

        /** 模块描述 */
        @Schema(description = "模块描述", example = "学生信息管理模块")
        private String moduleDesc;

        /** 排序顺序，数字越小越靠前 */
        @Schema(description = "排序顺序（数字越小越靠前）", example = "1")
        private Integer sortOrder;
    }
}
