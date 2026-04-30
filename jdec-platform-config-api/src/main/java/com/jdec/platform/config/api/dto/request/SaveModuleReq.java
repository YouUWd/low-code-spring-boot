package com.jdec.platform.config.api.dto.request;

import lombok.Data;
import java.util.List;

/**
 * 保存模块完整信息请求 DTO
 * 包含模块基本信息、关联表、字段配置和状态
 */
@Data
public class SaveModuleReq {

    /**
     * 模块基本信息
     */
    private CreateSysModuleReq module;

    /**
     * 模块关联表列表
     */
    private List<SaveModuleTableReq> moduleTables;

    /**
     * 模块字段配置列表
     */
    private List<SaveModuleFieldReq> moduleFields;

    /**
     * 模块状态列表
     */
    private List<SaveModuleStatusReq> moduleStatuses;

    /**
     * 保存模块关联表请求
     */
    @Data
    public static class SaveModuleTableReq {
        private Long id;
        private String tableName;
        private String tableDesc;
        private String joinLeftField;
        private String joinRightField;
        private String relationType;
        private Integer sortOrder;
    }

    /**
     * 保存模块字段请求
     */
    @Data
    public static class SaveModuleFieldReq {
        private Long id;
        private String fieldCode;
        private String displayName;
        private String logicalField;
        private List<SourceMappingItem> sourceMapping;
        private String transformer;
        private String transformerEnv;
        private String renderIcon;
        private String renderType;
        private Integer sortOrder;
        private Integer visible;
        private Integer required;
        private Integer searchable;
        private Integer sortable;
        private String defaultValue;
        private String validationRule;
    }

    /**
     * 保存模块状态请求
     */
    @Data
    public static class SaveModuleStatusReq {
        private Long id;
        private Long statusId;
    }
}
