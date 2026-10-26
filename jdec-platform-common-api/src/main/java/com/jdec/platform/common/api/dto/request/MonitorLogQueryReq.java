package com.jdec.platform.common.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 监控日志分页查询请求
 *
 * <p>时间范围作用于 {@code record_time}，类型筛选作用于 {@code type}，关键词同时模糊匹配 {@code content} 与 {@code
 * remark}；结果按记录时间倒序。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "监控日志分页查询请求")
public class MonitorLogQueryReq implements Serializable {

    @Schema(description = "页码（从 1 开始）", example = "1")
    private Integer pageNo;

    @Schema(description = "每页条数", example = "20")
    private Integer pageSize;

    @Schema(description = "记录时间起（含），仅日期，按当天 00:00:00 查询", example = "2026-09-01")
    private LocalDate startTime;

    @Schema(description = "记录时间止（含），仅日期，按当天 23:59:59 查询", example = "2026-09-30")
    private LocalDate endTime;

    @Schema(description = "日志类型：0-操作日志，1-消息日志，2-系统日志，为空时不过滤", example = "0")
    private Integer type;

    @Schema(description = "关键词，同时模糊匹配日志内容与备注")
    private String keyword;
}
