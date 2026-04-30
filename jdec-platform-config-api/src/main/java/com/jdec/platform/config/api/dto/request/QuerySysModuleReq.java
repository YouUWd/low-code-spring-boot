package com.jdec.platform.config.api.dto.request;

import lombok.Data;

/**
 * 查询模块请求 DTO
 */
@Data
public class QuerySysModuleReq {

    /**
     * 租户 ID
     */
    private String tenantId;

    /**
     * 模块编码（模糊查询）
     */
    private String moduleCode;

    /**
     * 模块名称（模糊查询）
     */
    private String moduleName;

    /**
     * 模块类型: query-查询模块, folder-文件夹, custom-自定义
     */
    private String moduleType;

    /**
     * 模块状态: active-启用, inactive-禁用, archived-归档
     */
    private String moduleStatus;

    /**
     * 模块层级
     */
    private Integer moduleLevel;

    /**
     * 是否为叶子节点: 0-否, 1-是
     */
    private Integer leafFlag;
}
