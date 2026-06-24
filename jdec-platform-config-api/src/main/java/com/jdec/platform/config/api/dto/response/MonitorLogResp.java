package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/** 监控日志响应 */
@Data
@Schema(description = "监控日志响应")
public class MonitorLogResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "监控类型")
    private String monitorType;

    @Schema(description = "日志类型")
    private String logType;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "登录用户ID")
    private Long loginUserId;

    @Schema(description = "模拟用户ID")
    private Long mockUserId;

    @Schema(description = "模拟标志 0:否 1:是")
    private Integer mockFlag;

    @Schema(description = "登录角色")
    private String loginRole;

    @Schema(description = "模拟角色")
    private String mockRole;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "用户信息")
    private String userInfo;

    @Schema(description = "IP地址")
    private String ipAddr;

    @Schema(description = "设备信息")
    private String device;

    @Schema(description = "主体ID")
    private Long subjectId;

    @Schema(description = "日志时间")
    private LocalDateTime logDate;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
