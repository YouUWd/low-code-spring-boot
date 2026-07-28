package com.jdec.platform.monitorlog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jdec.platform.monitorlog.biz.entity.enumtype.LogTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 系统监控系统控制日志实体 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_monitor_systemcontrollog")
@Schema(description = "系统监控系统控制日志")
public class JdecMonitorSystemControlLog extends JdecMonitorLogBase {

    @Schema(description = "系统控制日志记录类型")
    private LogTypeEnum logRecordType;
}
