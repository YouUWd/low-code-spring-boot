package com.jdec.platform.monitorlog.api.dto.request;

import cn.hutool.core.date.LocalDateTimeUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/** 记录兼容日志请求 */
@Data
@Schema(description = "监控日志列表请求")
public class MonitorLogPageListReq {

    @Schema(description = "搜索关键词（content 模糊匹配）")
    private String keyWord;

    @Schema(description = "日志类型")
    private String logType;

    @Schema(description = "分页查询-当前页码", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "分页查询-当前页码不能为空")
    private Long pageNum;

    @Schema(description = "分页查询-每页数量", example = "50", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "分页查询-每页数量不能为空")
    private Long pageSize;

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

    @Schema(description = "是否加载中（暂未使用）", defaultValue = "true")
    private Boolean noLoading = true;

    @Schema(
            description = "最后一条时间",
            example = "2026-06-25 10:00:00",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "最后一条时间不能为空")
    private String lastTime = LocalDateTimeUtil.format(LocalDateTime.now(), "yyyy-MM-dd HH:mm:ss");

    @Schema(
            description = "最后一条id",
            example = "999999999",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "最后一条id不能为空")
    private Long lastId = 999999999L;
}
