package com.jdec.platform.config.api.dto.response;

import com.jdec.platform.config.api.dto.common.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** 模块完整信息响应 DTO 包含模块基本信息、字段配置和状态 */
@Data
@Schema(description = "模块完整信息响应")
public class SysModuleResp {

    /** 模块基本信息 */
    @Schema(description = "模块基本信息")
    private ModuleInfo module;

    /** 模块字段列表 (首个字段所属表为主表) */
    @Schema(description = "模块字段列表")
    private List<ModuleFieldDTO> fields;

    /** 模块状态列表 */
    @Schema(description = "模块状态列表")
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

        @Schema(description = "模块类别: 1-业务模块, 2-系统模块", example = "1")
        private Integer category;

        @Schema(description = "数据来源主体ID集合")
        private List<Long> sourceSubjects;

        @Schema(description = "父模块ID", example = "0")
        private Long parentId;

        /** 主表名称 */
        @Schema(description = "主表名称", example = "student")
        private String primaryTable;

        @Schema(description = "是否开启启停功能: 0-否, 1-是", example = "0")
        private Integer enableActive;

        @Schema(description = "变更申请模块ID")
        private Long changeModuleId;

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
