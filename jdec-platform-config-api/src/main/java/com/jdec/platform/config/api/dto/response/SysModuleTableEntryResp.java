package com.jdec.platform.config.api.dto.response;

import java.time.LocalDateTime;
import lombok.Data;

/** 模块表条目响应 DTO 用于列表展示 */
@Data
public class SysModuleTableEntryResp {

    /** 表 ID */
    private Long id;

    /** 模块 ID */
    private Long moduleId;

    /** 表名 */
    private String tableName;

    /** 关系类型: 1:1-一对一, N:1-多对一, 1:N-一对多 */
    private String relationType;

    /** 关联表是否只读：0-否, 1-是 */
    private Integer readOnly;

    /** 排序顺序 */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createdDate;

    /** 更新时间 */
    private LocalDateTime updatedDate;
}
