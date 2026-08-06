package com.jdec.platform.monitorlog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_monitor_messagelog")
@Schema(description = "消息日志")
public class JdecMonitorMessageLog extends JdecMonitorLogBase {

    @Serial private static final long serialVersionUID = 1L;

    @Schema(description = "动作名称")
    @TableField("actionName")
    private String actionName;

    @Schema(description = "审批动作图标")
    @TableField("actionPath")
    private String actionPath;

    @Schema(description = "审批结果")
    @TableField("actionTypeTitle")
    private String actionTypeTitle;
}
