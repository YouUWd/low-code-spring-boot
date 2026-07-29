package com.jdec.platform.monitorlog.api.dto.request;

import cn.hutool.core.date.DateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 记录兼容日志请求 */
@Data
@Schema(description = "记录监控日志请求")
public class MonitorLogReq {

    @Schema(description = "ID")
    private Long id;

    @Schema(
            description =
                    "日志类型，例如：'system-log','operation-log','message-log'"
                            + "当日志类型为operation-log时，content的格式为：",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "日志类型不能为空")
    private String logType;

    @Schema(description = "主体ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "主体ID不能为空")
    private Long subjectId;

    @Schema(description = "登录用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "登录用户ID不能为空")
    private Long loginUserId;

    @Schema(description = "用户信息")
    private String userInfo;

    @Schema(description = "登录角色ID")
    private Long loginRoleId;

    @Schema(description = "是否mock标记：0，否；1 是")
    private Integer mockFlag = 0;

    @Schema(description = "模拟用户ID")
    private Long mockUserId;

    @Schema(description = "模拟角色ID")
    private Long mockRoleId;

    @Schema(description = "IP地址")
    private String ip;

    @Schema(description = "浏览器")
    private String browser = "unknown";

    @Schema(description = "平台信息")
    private String platForm = "unknown";

    @Schema(description = "日志内容")
    private String logContent;

    @Schema(description = "日志备注")
    private String remark;

    @Schema(description = "日志时间")
    private DateTime logDate = new DateTime();
}
