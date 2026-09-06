package com.jdec.platform.config.api.dto.response;

import com.jdec.platform.config.api.dto.common.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** 模块元数据响应 DTO 包含模块基本信息、关联表拓扑、字段配置、表头配置和状态 */
@Data
@Schema(description = "模块元数据响应")
public class SysModuleMetaResp {

    /** 模块基本信息 */
    @Schema(description = "模块基本信息")
    private ModuleInfo module;

    /** 模块涉及表的全局关联拓扑 (由后端根据字段自动推导并组装透传) */
    @Schema(description = "模块涉及表的全局关联拓扑")
    private List<TableRelationDTO> tableRelations;

    /** 模块字段列表 (首个字段所属表为主表) */
    @Schema(description = "模块字段列表")
    private List<ModuleFieldDTO> fields;

    /** 模块列表表头配置 */
    @Schema(description = "模块列表表头配置")
    private List<ModuleTableHeaderDTO> moduleHeaders;

    /** 模块状态列表 */
    @Schema(description = "模块状态列表")
    private List<ModuleStatusDTO> moduleStatuses;

    /** 当前根模块辖下的子孙模块树节点列表 */
    @Schema(description = "当前根模块辖下的子孙模块树节点列表")
    private List<ModuleNodeDTO> moduleNodes;

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

        @Schema(description = "物理主表名(必填)", example = "student")
        private String primaryTable;

        @Schema(description = "父模块ID", example = "0")
        private Long parentId;

        @Schema(description = "排序顺序", example = "1")
        private Integer sortOrder;

        @Schema(description = "创建人ID", example = "1")
        private Long createdBy;

        @Schema(description = "创建时间")
        private LocalDateTime createdDate;

        @Schema(description = "更新人ID", example = "2")
        private Long updatedBy;

        @Schema(description = "更新时间")
        private LocalDateTime updatedDate;
    }
}
