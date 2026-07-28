package com.jdec.platform.monitorlog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 系统监控导航切换日志实体 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_monitor_navchangelog")
@Schema(description = "系统监控导航切换日志")
public class JdecMonitorNavChangeLog extends JdecMonitorLogBase {

    // 空类，所有字段继承自基类
}
