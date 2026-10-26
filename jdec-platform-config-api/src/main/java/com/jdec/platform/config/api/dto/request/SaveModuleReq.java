package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.config.api.dto.common.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 保存模块完整信息请求 DTO 包含模块基本信息、字段配置、状态机 (主表由 fields 第一项自推导) */
@Data
@Schema(description = "保存模块完整信息请求")
public class SaveModuleReq {

    /** 模块基本信息 */
    @Schema(description = "模块基本信息", requiredMode = Schema.RequiredMode.REQUIRED)
    private SaveSysModuleReq module;

    /** 模块物理字段配置列表 */
    @Schema(description = "模块物理字段配置列表", example = "[]")
    private List<ModuleFieldDTO> fields;

    /** 模块状态配置列表 */
    @Schema(description = "模块状态配置列表", example = "[]")
    private List<ModuleStatusDTO> moduleStatuses;

    /** 调整后的模块树节点拓扑（可选，传入时同步批量更新父子关系与同级排序） */
    @Schema(description = "调整后的模块树节点关系（可选，传入时同步批量更新父子关系与排序）")
    private List<ModuleTreeNodeDTO> treeNodes;

    /** 模块树节点位置信息 DTO */
    @Data
    @Schema(description = "模块树节点位置信息")
    public static class ModuleTreeNodeDTO {

        @Schema(description = "模块ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
        private Long id;

        @Schema(
                description = "父模块ID（0表示根节点）",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "0")
        private Long parentId;

        @Schema(description = "同级排序序号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        private Integer sortOrder;
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

        /** 模块类别: 1-业务模块, 2-系统模块 */
        @Schema(description = "模块类别: 1-业务模块, 2-系统模块", example = "1")
        private Integer category;

        /** 主表名称 */
        @Schema(description = "主表名称", example = "student")
        private String primaryTable;

        /** 是否开启启停功能: 0-否, 1-是 */
        @Schema(description = "是否开启启停功能: 0-否, 1-是", example = "0")
        private Integer enableActive;

        /** 变更申请模块ID */
        @Schema(description = "变更申请模块ID")
        private Long changeModuleId;

        /** 数据来源主体ID集合 */
        @Schema(description = "数据来源主体ID集合")
        private List<Long> sourceSubjects;
    }
}
