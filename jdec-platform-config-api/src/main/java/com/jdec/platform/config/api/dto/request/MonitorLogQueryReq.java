package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 监控日志查询请求 */
@Data
@Schema(description = "监控日志查询请求")
public class MonitorLogQueryReq {

    @Schema(description = "日志类型")
    private String logType;

    @Schema(description = "关键词（content 模糊匹配）")
    private String keyWord;

    @Schema(
            description = "开始时间",
            example = "2026-06-24",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "开始时间不能为空")
    private String startTime;

    @Schema(
            description = "结束时间",
            example = "2026-06-25",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "结束时间不能为空")
    private String endTime;

    @Schema(description = "查询条数", example = "50", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "查询条数不能为空")
    private Integer size;

    @Schema(description = "是否加载中（暂未使用）", defaultValue = "true")
    private Boolean noLoading = true;
}
