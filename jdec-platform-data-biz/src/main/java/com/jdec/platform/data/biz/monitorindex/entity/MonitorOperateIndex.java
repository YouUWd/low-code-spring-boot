package com.jdec.platform.data.biz.monitorindex.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/**
 * 操作日志索引（业务库 monitor_operate_index 行模型，经 jOOQ 按 projectNo 动态解析数据源读写）
 *
 * <p>表无 project_no 列，查询不做数据隔离过滤。
 */
@Data
@Schema(description = "操作日志索引")
public class MonitorOperateIndex implements Serializable {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "日志id")
    private Long logId;

    @Schema(description = "模块id")
    private Long moduleId;

    @Schema(description = "单据id")
    private Long dataId;
}
