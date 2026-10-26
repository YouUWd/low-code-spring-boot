package com.jdec.platform.config.api.dto.request;

import lombok.Data;

/** 查询模块表请求 DTO */
@Data
public class QuerySysModuleTableReq {

    /** 模块 ID */
    private Long moduleId;

    /** 表名（模糊查询） */
    private String tableName;

    /** 关系类型: 1:1-一对一, N:1-多对一, 1:N-一对多 */
    private String relationType;

    /** 关联表是否只读：0-否, 1-是 */
    private Integer readOnly;
}
