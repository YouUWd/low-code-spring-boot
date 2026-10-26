package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/** 创建定时 HTTP 任务请求 DTO */
@Data
@Schema(description = "创建定时 HTTP 任务请求")
public class CreateScheduledTaskReq {

    @Schema(description = "项目编号(为空时取当前上下文)", example = "student_manage")
    private String projectNo;

    @Schema(description = "主体 ID(为空时取当前上下文)", example = "1")
    private Long subjectId;

    @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "同步部门数据")
    private String taskName;

    @Schema(
            description = "计划执行时间",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "2026-08-14 18:00:00")
    private LocalDateTime executeTime;

    @Schema(
            description = "请求地址",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "https://example.com/api/sync")
    private String requestUrl;

    @Schema(
            description = "请求方式: GET, POST, PUT, DELETE",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "POST")
    private String requestMethod;

    @Schema(
            description = "请求头(JSON 字符串, Map 格式)",
            example = "{\"Content-Type\":\"application/json\"}")
    private String requestHeaders;

    @Schema(description = "请求体", example = "{\"pageNo\":1,\"pageSize\":10}")
    private String requestBody;

    @Schema(
            description = "请求参数(JSON 字符串, Map 格式, 拼接到 URL query string)",
            example = "{\"token\":\"abc123\"}")
    private String requestParams;
}
