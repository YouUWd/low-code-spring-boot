package com.jdec.platform.config.api.dto.request;

import lombok.Data;

/**
 * 查询模块字段请求 DTO
 */
@Data
public class QuerySysModuleFieldReq {

    /**
     * 模块 ID
     */
    private Long moduleId;

    /**
     * 字段编码（模糊查询）
     */
    private String fieldCode;

    /**
     * 字段显示名称（模糊查询）
     */
    private String displayName;

    /**
     * 逻辑字段名
     */
    private String logicalField;

    /**
     * 转换环境: database-数据库级, frontend-前端级, none-无转换
     */
    private String transformerEnv;

    /**
     * 是否可见: 0-不可见, 1-可见
     */
    private Integer visible;

    /**
     * 是否可搜索: 0-不可搜索, 1-可搜索
     */
    private Integer searchable;

    /**
     * 是否可排序: 0-不可排序, 1-可排序
     */
    private Integer sortable;
}
