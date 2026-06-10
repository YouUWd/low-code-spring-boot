package com.jdec.platform.config.api.dto.request;

import lombok.Data;

/** 更新模块表请求 DTO */
@Data
public class UpdateSysModuleTableReq {

    /** 表 ID */
    private Long id;

    /** 表名 */
    private String tableName;

    /** 表描述 */
    private String tableDesc;

    /** 关联左字段（关联表中的字段） */
    private String joinLeftField;

    /** 关联右字段（主表中的字段） */
    private String joinRightField;

    /** 关系类型: 1:1-一对一, N:1-多对一, 1:N-一对多 */
    private String relationType;

    /** 关联表是否只读：0-否, 1-是 */
    private Integer readOnly;

    /** 排序顺序 */
    private Integer sortOrder;

    /** 更新人 ID */
    private String updatedBy;

    /** 更新人姓名 */
    private String updatedName;
}
