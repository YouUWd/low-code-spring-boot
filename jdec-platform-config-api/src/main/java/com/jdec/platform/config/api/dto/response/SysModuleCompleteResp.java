package com.jdec.platform.config.api.dto.response;

import com.jdec.platform.config.api.enums.ModuleTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** 模块完整信息响应 DTO 包含模块基本信息、关联表、字段配置和状态 */
@Data
@Schema(description = "模块完整信息响应")
public class SysModuleCompleteResp {

    /** 模块基本信息 */
    @Schema(description = "模块基本信息")
    private ModuleInfo module;

    /** 模块关联表列表 */
    @Schema(description = "模块关联表列表")
    private List<ModuleTableInfo> moduleTables;

    /** 模块简单字段列表 */
    @Schema(description = "模块简单字段列表")
    private List<SimpleFieldInfo> simpleFields;

    /** 模块组合字段列表 */
    @Schema(description = "模块组合字段列表")
    private List<CombineFieldInfo> combineFields;

    /** 模块状态列表（仅DETAIL类型） */
    @Schema(description = "模块状态列表（仅DETAIL类型）")
    private List<ModuleStatusInfo> moduleStatuses;

    /** 模块基本信息 */
    @Data
    @Schema(description = "模块基本信息")
    public static class ModuleInfo {
        @Schema(description = "模块ID", example = "1")
        private Long id;

        @Schema(description = "项目编码", example = "P260319144021")
        private String projectNo;

        @Schema(description = "模块编码", example = "MOD-STUDENT-FULL")
        private String moduleCode;

        @Schema(description = "模块名称", example = "学生管理")
        private String moduleName;

        @Schema(description = "模块描述", example = "学生信息管理模块")
        private String moduleDesc;

        @Schema(description = "父模块ID", example = "1")
        private Long parentId;

        @Schema(description = "模块层级", example = "1")
        private Integer moduleLevel;

        @Schema(description = "模块路径", example = "/1/3/5/")
        private String modulePath;

        @Schema(description = "主表名称", example = "student")
        private String primaryTable;

        @Schema(
                description = "模块类型",
                example = "LIST",
                allowableValues = {"LIST", "DETAIL"})
        private ModuleTypeEnum moduleType;

        @Schema(
                description = "模块状态",
                example = "active",
                allowableValues = {"active", "inactive", "archived"})
        private String moduleStatus;

        @Schema(description = "是否叶子节点", example = "1")
        private Integer leafFlag;

        @Schema(description = "排序顺序", example = "1")
        private Integer sortOrder;

        @Schema(description = "列表表头配置（JSON格式）")
        private String tableHeader;

        @Schema(description = "创建人ID", example = "1")
        private Long createdBy;

        @Schema(description = "创建时间")
        private LocalDateTime createdDate;

        @Schema(description = "创建人姓名", example = "张三")
        private String createdName;

        @Schema(description = "更新人ID", example = "2")
        private Long updatedBy;

        @Schema(description = "更新时间")
        private LocalDateTime updatedDate;

        @Schema(description = "更新人姓名", example = "李四")
        private String updatedName;
    }

    /** 模块关联表信息 */
    @Data
    @Schema(description = "模块关联表信息")
    public static class ModuleTableInfo {
        @Schema(description = "关联ID", example = "1")
        private Long id;

        @Schema(description = "表名", example = "course")
        private String tableName;

        @Schema(description = "表描述", example = "课程表")
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

    /** 简单字段信息 */
    @Data
    @Schema(description = "简单字段信息")
    public static class SimpleFieldInfo {
        @Schema(description = "字段ID", example = "1")
        private Long id;

        @Schema(description = "表名", example = "student")
        private String tableName;

        @Schema(description = "列名", example = "student_no")
        private String columnName;

        @Schema(description = "转换表达式", example = "UPPER(${student_no})")
        private String transformer;
    }

    /** 组合字段信息 */
    @Data
    @Schema(description = "组合字段信息")
    public static class CombineFieldInfo {
        @Schema(description = "字段ID", example = "1")
        private Long id;

        @Schema(description = "逻辑字段名", example = "fullName")
        private String logicalField;

        @Schema(description = "字段显示名称", example = "全名")
        private String displayName;

        @Schema(description = "物理字段映射（JSON格式）")
        private String sourceMapping;

        @Schema(description = "转换表达式", example = "CONCAT(${last_name}, ${first_name})")
        private String transformer;

        @Schema(description = "是否启用", example = "1")
        private Integer enabled;
    }

    /** 模块状态信息 */
    @Data
    @Schema(description = "模块状态信息")
    public static class ModuleStatusInfo {
        @Schema(description = "状态ID", example = "1")
        private Long id;

        @Schema(description = "状态父ID", example = "1")
        private Long statusPid;

        @Schema(description = "状态ID", example = "10")
        private Long statusId;
    }
}
