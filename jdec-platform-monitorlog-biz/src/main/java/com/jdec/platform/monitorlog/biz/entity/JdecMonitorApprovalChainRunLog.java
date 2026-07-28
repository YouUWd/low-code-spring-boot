package com.jdec.platform.monitorlog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 系统监控审批链执行日志实体 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_monitor_ApprovalChainRunlog")
@Schema(description = "系统监控审批链执行日志")
public class JdecMonitorApprovalChainRunLog extends JdecMonitorLogBase {

    // 空类，所有字段继承自基类
}
