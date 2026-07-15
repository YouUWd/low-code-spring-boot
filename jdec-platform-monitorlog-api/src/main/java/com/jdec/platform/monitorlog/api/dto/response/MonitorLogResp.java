package com.jdec.platform.monitorlog.api.dto.response;

import cn.hutool.core.date.DateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/** 监控日志响应 */
@Data
@Builder
@Schema(description = "监控日志响应")
public class MonitorLogResp {

    @Schema(description = "主题ID")
    private Long subjectId;

    @Schema(description = "监控类型")
    private String monitorType;

    @Schema(description = "日志类型")
    private String logType;

    @Schema(description = "日志内容")
    private String logContent;

    @Schema(description = "日志备注")
    private String logRemark;

    @Schema(description = "登录用户ID")
    private Long loginUserId;

    @Schema(description = "用户信息")
    private String userInfo;

    @Schema(description = "模拟用户ID")
    private Long mockUserId;

    @Schema(description = "登录角色ID")
    private Long loginRoleId;

    @Schema(description = "登录角色名称")
    private String loginRoleName;

    @Schema(description = "是否mock标记：0，否；1 是")
    private Integer mockFlag;

    @Schema(description = "模拟角色ID")
    private Long mockRoleId;

    @Schema(description = "IP地址")
    private String ip;

    @Schema(description = "浏览器")
    private String browser;

    @Schema(description = "平台信息")
    private String platForm;

    @Schema(description = "日志时间")
    private DateTime logDate;
}
