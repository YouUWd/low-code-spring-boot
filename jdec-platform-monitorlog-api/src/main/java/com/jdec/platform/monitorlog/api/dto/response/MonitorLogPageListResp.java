package com.jdec.platform.monitorlog.api.dto.response;

import cn.hutool.core.date.DateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 监控日志响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "监控日志响应")
public class MonitorLogPageListResp {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "监控类型")
    private String monitorType;

    @Schema(description = "日志类型")
    private String logType;

    @Schema(description = "日志类型标签")
    private String logTypeLabel;

    @Schema(description = "日志内容")
    private String logContent;

    @Schema(description = "日志备注")
    private String remark;

    @Schema(description = "是否mock标记：0，否；1 是")
    private Integer isMock;

    @Schema(description = "登录角色")
    private Long loginRole;

    @Schema(description = "模拟角色ID")
    private Long mockRole;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "用户信息")
    private String userInfo;

    @Schema(description = "日志时间")
    private DateTime logDate;

    @Schema(description = "创建时间")
    private DateTime createdDate;
}
