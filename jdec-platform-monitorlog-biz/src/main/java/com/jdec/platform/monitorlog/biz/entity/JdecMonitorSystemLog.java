package com.jdec.platform.monitorlog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_monitor_systemlog")
@Schema(description = "系统日志")
public class JdecMonitorSystemLog extends JdecMonitorLogBase {}
