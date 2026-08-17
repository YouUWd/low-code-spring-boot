package com.jdec.platform.config.biz.audit.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 操作详情
 *
 * <p>包含新增(i)、修改(u)、删除(d)三种操作类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionDetail {

    /** 新增操作（insert） */
    private List<RecordChange> i;

    /** 修改操作（update） */
    private List<RecordChange> u;

    /** 删除操作（delete） */
    private List<RecordChange> d;
}
