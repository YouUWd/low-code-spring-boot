package com.jdec.platform.config.biz.audit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 记录变更
 *
 * <p>表示单条记录的变更情况
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecordChange {

    /** 记录名称（通过@AuditField标注的唯一标识字段） */
    private String name;

    /** 字段变更列表 */
    private List<ColumnChange> columns;
}
