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

    @Schema(description = "日志内容")
    private String logContent;

    @Schema(description = "日志备注")
    private String logRemark;

    @Schema(description = "登录用户ID")
    private Long loginUserId;

    @Schema(description = "模拟用户ID")
    private Long mockUserId;

    @Schema(description = "是否mock标记：0，否；1 是")
    private Integer mockFlag;

    @Schema(description = "登录角色")
    private Long loginRole;

    @Schema(description = "模拟角色ID")
    private Long mockRole;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "用户信息")
    private String userInfo;

    @Schema(description = "IP地址")
    private String ip;

    @Schema(description = "浏览器")
    private String browser;

    @Schema(description = "平台信息")
    private String platForm;

    @Schema(description = "日志时间")
    private DateTime logDate;

    @Schema(description = "创建人ID")
    private Long createdBy;

    @Schema(description = "创建人名称")
    private String createdName;

    @Schema(description = "创建时间")
    private DateTime createdDate;

    @Schema(description = "更新人ID")
    private Long updatedBy;

    @Schema(description = "更新人名称")
    private String updatedName;

    @Schema(description = "更新时间")
    private DateTime updatedDate;

    @Schema(description = "日志状态：1，删除；0，正常。")
    private Integer deleted;
}
