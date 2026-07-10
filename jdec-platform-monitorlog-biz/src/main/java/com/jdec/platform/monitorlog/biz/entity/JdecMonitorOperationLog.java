package com.jdec.platform.monitorlog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 操作日志实体 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jdec_monitor_operation_log")
@Schema(description = "操作日志")
public class JdecMonitorOperationLog extends JdecMonitorLogBase {
    // 空类，所有字段继承自基类
}
