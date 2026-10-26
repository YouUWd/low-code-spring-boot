package com.jdec.platform.config.biz.audit.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 审计结果 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditResult {

    /** 业务模块 */
    private String module;

    /** 业务子模块 */
    private String subModule;

    /** 操作类型 */
    private String operation;

    /** 表名 */
    private String tableName;

    /** 数据ID */
    private Long dataId;

    /** 字段差异列表 */
    private List<FieldDiff> diffs;

    /** 操作时间 */
    private LocalDateTime operateTime;

    /** 记录名称（通过@AuditField标注的唯一标识字段值） */
    private String recordName;
}
