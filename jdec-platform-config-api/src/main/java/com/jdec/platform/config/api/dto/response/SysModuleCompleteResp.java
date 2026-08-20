package com.jdec.platform.config.api.dto.response;

import com.jdec.platform.config.api.dto.common.*;
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
    private List<ModuleTableDTO> moduleTables;

    /** 模块简单字段列表 */
    @Schema(description = "模块简单字段列表")
    private List<ModuleSimpleFieldDTO> simpleFields;

    //    /** 模块组合字段列表 */
    //    @Schema(description = "模块组合字段列表")
    //    private List<ModuleCombineFieldDTO> combineFields;

    /** 模块状态列表（仅DETAIL类型） */
    @Schema(description = "模块状态列表（仅DETAIL类型）")
    private List<ModuleStatusDTO> moduleStatuses;

    /** 模块基本信息 */
    @Data
    @Schema(description = "模块基本信息")
    public static class ModuleInfo {
        @Schema(description = "模块ID", example = "1")
        private Long id;

        @Schema(description = "项目编码", example = "P260319144021")
        private String projectNo;

        @Schema(description = "主体ID", example = "1")
        private Long subjectId;

        @Schema(description = "模块编码", example = "MOD-STUDENT-FULL")
        private String moduleCode;

        @Schema(description = "模块名称", example = "学生管理")
        private String moduleName;

        @Schema(description = "模块描述", example = "学生信息管理模块")
        private String moduleDesc;

        @Schema(description = "父模块ID", example = "1")
        private Long parentId;

        @Schema(description = "详情模块ID", example = "2")
        private Long detailModuleId;

        @Schema(description = "主表名称", example = "student")
        private String primaryTable;

        @Schema(
                description = "模块类型",
                example = "LIST",
                allowableValues = {"LIST", "DETAIL"})
        private ModuleTypeEnum moduleType;

        @Schema(description = "是否启用审批", example = "0")
        private Integer approvalRequired;

        @Schema(description = "是否模块业务定义", example = "0")
        private Integer bizDefFlag;

        @Schema(description = "模块类别 (1=业务模块, 2=系统模块)", example = "1")
        private Integer category;

        @Schema(description = "主表被设置为bizDefFlag的模块表信息集合")
        private List<TableModule> bizDefTables;

        @Schema(description = "子表被设置为readOnly=0的模块表信息集合")
        private List<TableModule> writableRelationTables;

        @Schema(description = "排序顺序", example = "1")
        private Integer sortOrder;

        @Schema(description = "列表表头配置")
        private List<ModuleTableHeaderDTO> tableHeader;

        @Schema(description = "数据来源主体")
        private List<SubjectInfo> sourceSubjects;

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

        @Schema(description = "主表外键关联字段", example = "user_id")
        private String relateSearchField;
    }

    /** 主体简要信息 */
    @Data
    @Schema(description = "主体简要信息")
    public static class SubjectInfo {
        @Schema(description = "主体ID", example = "1")
        private Long id;

        @Schema(description = "主体名称", example = "某某主体")
        private String subjectName;
    }

    /** 模块表信息 */
    @Data
    @Schema(description = "模块表信息")
    public static class TableModule {
        @Schema(description = "表名", example = "sys_user")
        private String tableName;

        @Schema(description = "模块ID", example = "1")
        private Long moduleId;
    }
}
