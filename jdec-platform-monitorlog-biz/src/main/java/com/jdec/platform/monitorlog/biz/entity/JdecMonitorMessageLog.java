package com.jdec.platform.monitorlog.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 消息日志实体 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_monitor_messagelog")
@Schema(description = "消息日志")
public class JdecMonitorMessageLog extends JdecMonitorLogBase {}
