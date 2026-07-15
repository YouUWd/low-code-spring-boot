package com.jdec.platform.monitorlog.biz.controller;

import com.jdec.platform.monitorlog.api.dto.request.MonitorLogPageListReq;
import com.jdec.platform.monitorlog.api.dto.request.MonitorLogReq;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogPageListResp;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogResp;
import com.jdec.platform.monitorlog.biz.service.JdecMonitorLogService;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 监控日志 REST 控制器
 *
 * <p>提供监控日志的查询接口
 */
@RestController
@RequestMapping("/api/monitorlog")
@RequiredArgsConstructor
@Tag(name = "监控日志", description = "监控日志查询接口")
public class JdecMonitorLogController {

    private final JdecMonitorLogService jdecMonitorLogService;

    @PostMapping("/queryPage")
    @Operation(summary = "查询监控日志列表", description = "根据时间范围、日志类型、关键词查询监控日志")
    public ApiResponse<List<MonitorLogPageListResp>> query(
            @Valid @RequestBody MonitorLogPageListReq request) {
        return ApiResponse.success(jdecMonitorLogService.queryPageList(request));
    }

    @PostMapping("/Add")
    @Operation(summary = "添加监控日志", description = "添加监控日志")
    public ApiResponse<Void> add(@Valid @RequestBody MonitorLogReq request) throws SQLException {
        jdecMonitorLogService.record(request);
        return ApiResponse.success();
    }

    @GetMapping("/get")
    @Operation(summary = "获取监控日志详情", description = "根据ID获取监控日志详情")
    public ApiResponse<MonitorLogResp> get(@RequestParam Long id, @RequestParam String logType) {
        if (logType == null || logType.isEmpty()) {
            return ApiResponse.error("日志类型不能为空");
        }
        return ApiResponse.success(jdecMonitorLogService.get(id, logType));
    }
}
