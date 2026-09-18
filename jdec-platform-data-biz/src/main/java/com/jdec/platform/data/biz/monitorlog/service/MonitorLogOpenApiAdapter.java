package com.jdec.platform.data.biz.monitorlog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.open.api.OpenMonitorLogApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 监控日志开放接口适配器（{@code config-api} 侧接口的实现）
 *
 * <p>供 config 模块写入监控日志：把 config-api 的请求 DTO 转为数据引擎的同构 DTO 后落库。
 *
 * <p>{@code monitor_log} 表位于业务库，与调用方的业务库同源，故按请求上下文（{@code X-Project-No}）解析数据源， 不做任何写死。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorLogOpenApiAdapter implements OpenMonitorLogApi {

    private final MonitorLogService monitorLogService;
    private final ObjectMapper objectMapper;

    @Override
    public void saveLog(com.jdec.platform.config.api.dto.request.MonitorLogSaveReq req) {
        if (req == null) {
            return;
        }
        monitorLogService.saveLog(toEngineReq(req));
    }

    /** config-api 请求 DTO 转数据引擎请求 DTO（结构与多态标识一致，直接按 JSON 转换） */
    private com.jdec.platform.data.api.dto.request.MonitorLogSaveReq toEngineReq(
            com.jdec.platform.config.api.dto.request.MonitorLogSaveReq req) {
        return objectMapper.convertValue(
                req, com.jdec.platform.data.api.dto.request.MonitorLogSaveReq.class);
    }
}
