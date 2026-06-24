package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.dto.request.MonitorLogQueryReq;
import com.jdec.platform.config.api.dto.response.MonitorLogResp;
import com.jdec.platform.config.biz.service.JdecMonitorLogService;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 监控日志 REST 控制器
 *
 * <p>提供监控日志的查询接口
 */
@RestController
@RequestMapping("/api/config/monitor-log")
@RequiredArgsConstructor
@Tag(name = "监控日志", description = "监控日志查询接口")
public class JdecMonitorLogController {

    private final JdecMonitorLogService jdecMonitorLogService;

    @PostMapping("/query")
    @Operation(summary = "查询监控日志列表", description = "根据时间范围、日志类型、关键词查询监控日志")
    public ApiResponse<List<MonitorLogResp>> query(@Valid @RequestBody MonitorLogQueryReq request) {
        return ApiResponse.success(jdecMonitorLogService.queryList(request));
    }
}
