package com.jdec.platform.monitorlog.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 监控日志基类 */
@Data
public class JdecMonitorLogBase implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "主题ID")
    private Long subjectId;

    @Schema(description = "登录用户ID")
    private Long loginUserId;

    @Schema(description = "用户信息")
    private String userInfo;

    @Schema(description = "登录角色ID")
    private Long loginRoleId;

    @Schema(description = "登录角色名称")
    private String loginRoleName;

    @Schema(description = "是否模拟 0:否 1:是")
    private Integer mockFlag;

    @Schema(description = "模拟用户ID")
    private Long mockUserId;

    @Schema(description = "模拟用户名称")
    private String mockUserName;

    @Schema(description = "模拟角色ID")
    private Long mockRoleId;

    @Schema(description = "模拟角色名称")
    private String mockRoleName;

    @Schema(description = "IP地址")
    private String ip;

    @Schema(description = "设备信息")
    private String browser;

    @Schema(description = "平台信息")
    private String platForm;

    @Schema(description = "系统名称")
    private String systemName;

    @Schema(description = "日志内容")
    private String logContent;

    @Schema(description = "日志备注")
    @TableField("log_remark")
    private String remark;

    @Schema(description = "日志日期")
    private LocalDateTime logDate;

    @Schema(description = "日志类型")
    @TableField(exist = false) // 明确标识此字段不在数据库表中
    private String logType;
}
