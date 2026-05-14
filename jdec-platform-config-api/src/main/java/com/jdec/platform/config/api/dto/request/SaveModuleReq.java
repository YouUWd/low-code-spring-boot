package com.jdec.platform.config.api.dto.request;

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
    private List<SaveModuleTableReq> moduleTables;

    /** 模块简单字段配置列表 */
    @Schema(description = "模块简单字段配置列表（单表字段映射）", example = "[]")
    private List<SaveSimpleFieldReq> simpleFields;

    /** 模块组合字段配置列表 */
    @Schema(description = "模块组合字段配置列表（多表字段组合映射）", example = "[]")
    private List<SaveCombineFieldReq> combineFields;

    /** 模块状态配置列表（仅DETAIL类型模块需要） */
    @Schema(description = "模块状态配置列表（仅DETAIL类型模块需要）", example = "[]")
    private List<SaveModuleStatusReq> moduleStatuses;

    /** 保存模块关联表请求 */
    @Data
    @Schema(description = "模块关联表信息")
    public static class SaveModuleTableReq {

        @Schema(description = "表关联ID（更新时使用）", example = "1")
        private Long id;

        @Schema(
                description = "表名",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "student")
        private String tableName;

        @Schema(description = "表描述", example = "学生表")
        private String tableDesc;

        @Schema(description = "左表关联字段", example = "id")
        private String joinLeftField;

        @Schema(description = "右表关联字段", example = "student_id")
        private String joinRightField;

        @Schema(
                description = "关联关系类型",
                example = "1:N",
                allowableValues = {"1:1", "1:N", "N:1"})
        private String relationType;

        @Schema(description = "排序顺序", example = "1")
        private Integer sortOrder;
    }

    /** 保存模块简单字段请求 用于单表字段映射 */
    @Data
    @Schema(description = "模块简单字段配置（单表字段映射）")
    public static class SaveSimpleFieldReq {

        @Schema(description = "字段ID（更新时使用）", example = "1")
        private Long id;

        @Schema(
                description = "表名",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "student")
        private String tableName;

        @Schema(
                description = "列名",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "student_no")
        private String columnName;

        @Schema(description = "转换表达式（可选）", example = "UPPER(${student_no})")
        private String transformer;
    }

    /** 保存模块组合字段请求 用于多表字段组合映射 */
    @Data
    @Schema(description = "模块组合字段配置（多表字段组合映射）")
    public static class SaveCombineFieldReq {

        @Schema(description = "字段ID（更新时使用）", example = "1")
        private Long id;

        @Schema(
                description = "逻辑字段名（驼峰格式）",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "fullName")
        private String logicalField;

        @Schema(description = "字段显示名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "全名")
        private String displayName;

        @Schema(
                description = "物理字段映射（JSON格式）",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example =
                        "[{\"entity\":\"student\",\"field\":\"last_name\",\"sort_order\":1},{\"entity\":\"student\",\"field\":\"first_name\",\"sort_order\":2}]")
        private List<SourceMappingItem> sourceMapping;

        @Schema(description = "转换表达式", example = "CONCAT(${last_name}, ${first_name})")
        private String transformer;

        @Schema(
                description = "是否启用",
                example = "1",
                allowableValues = {"0", "1"})
        private Integer enabled;
    }

    /** 保存模块状态请求 仅DETAIL类型模块需要 */
    @Data
    @Schema(description = "模块状态配置")
    public static class SaveModuleStatusReq {

        @Schema(description = "状态父ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        private Long statusPid;

        @Schema(description = "状态ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
        private Long statusId;
    }

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

        /** 同级排序顺序，数字越小越靠前 */
        @Schema(description = "排序顺序（数字越小越靠前）", example = "1")
        private Integer sortOrder;

        /** 列表表头配置（仅LIST类型模块需要） */
        @Schema(description = "列表表头配置（仅LIST类型模块需要）")
        private List<TableHeaderColumn> tableHeader;
    }
}
