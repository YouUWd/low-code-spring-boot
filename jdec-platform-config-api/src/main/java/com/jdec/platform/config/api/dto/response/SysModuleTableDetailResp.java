package com.jdec.platform.config.api.dto.response;

import java.time.LocalDateTime;
import lombok.Data;

/** 模块表详情响应 DTO 用于详情页面 */
@Data
public class SysModuleTableDetailResp {

    /** 表 ID */
    private Long id;

    /** 模块 ID */
    private Long moduleId;

    /** 表名 */
    private String tableName;

    /** 表描述 */
    private String tableDesc;

    /** 关联左字段 */
    private String joinLeftField;

    /** 关联右字段 */
    private String joinRightField;

    /** 关系类型: 1:1-一对一, N:1-多对一, 1:N-一对多 */
    private String relationType;

    /** 关联表是否只读：0-否, 1-是 */
    private Integer readOnly;

    /** 排序顺序 */
    private Integer sortOrder;

    /** 创建人 ID */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdDate;

    /** 创建人姓名 */
    private String createdName;

    /** 更新人 ID */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedDate;

    /** 更新人姓名 */
    private String updatedName;
}
