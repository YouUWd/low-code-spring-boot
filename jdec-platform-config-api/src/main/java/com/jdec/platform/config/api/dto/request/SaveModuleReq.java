package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.config.api.dto.common.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 保存模块完整信息请求 DTO 包含模块基本信息、关联表、字段配置（简单字段+组合字段） */
@Data
@Schema(description = "保存模块完整信息请求")
public class SaveModuleReq {

    /** 模块基本信息 */
    @Schema(description = "模块基本信息", requiredMode = Schema.RequiredMode.REQUIRED)
    private SaveSysModuleReq module;

    /** 模块关联表列表 */
    @Schema(description = "模块关联表列表", example = "[]")
    private List<ModuleTableDTO> moduleTables;

    /** 模块简单字段配置列表 */
    @Schema(description = "模块简单字段配置列表（单表字段映射）", example = "[]")
    private List<ModuleSimpleFieldDTO> simpleFields;

    /** 模块状态配置列表（仅DETAIL类型模块需要） */
    @Schema(description = "模块状态配置列表（仅DETAIL类型模块需要）", example = "[]")
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

        /** 父模块 ID，NULL 表示根模块 */
        @Schema(description = "父模块ID（NULL表示根模块）", example = "1")
        private Long parentId;

        /** 详情模块 ID */
        @Schema(description = "详情模块ID", example = "2")
        private Long detailModuleId;

        /** 主表名称，folder 类型可为空 */
        @Schema(description = "主表名称", example = "student")
        private String primaryTable;

        /** 模块类型: LIST-列表, DETAIL-详情 */
        @Schema(
                description = "模块类型",
                example = "LIST",
                allowableValues = {"LIST", "DETAIL"})
        private String moduleType;

        /** 是否启用审批: 0-否, 1-是 */
        @Schema(description = "是否启用审批（0=否，1=是）", example = "0")
        private Integer approvalRequired;

        /** 是否模块业务定义: 0-否, 1-是 */
        @Schema(description = "是否模块业务定义（0=否，1=是）", example = "0")
        private Integer bizDefFlag;

        /** 模块类别: 1-业务模块, 2-系统模块 */
        @Schema(
                description = "模块类别（1=业务模块，2=系统模块）",
                example = "1",
                allowableValues = {"1", "2"})
        private Integer category;

        /** 同级排序顺序，数字越小越靠前 */
        @Schema(description = "排序顺序（数字越小越靠前）", example = "1")
        private Integer sortOrder;

        /** 列表表头配置（仅LIST类型模块需要） */
        @Schema(description = "列表表头配置（仅LIST类型模块需要）")
        private List<ModuleTableHeaderDTO> tableHeader;

        /** 数据来源主体，存储主体 ID 的数组 */
        @Schema(description = "数据来源主体")
        private List<Long> sourceSubjects;

        /** 主表外键关联字段 */
        @Schema(description = "主表外键关联字段", example = "user_id")
        private String relateSearchField;
    }
}
