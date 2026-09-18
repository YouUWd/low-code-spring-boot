package com.jdec.platform.config.open.api;

import com.jdec.platform.config.api.dto.request.MonitorLogSaveReq;

/**
 * 监控日志写入接口（config 侧消费，由数据引擎模块实现）
 *
 * <p>config 侧记录操作日志的统一出口：调用方按日志类型构造 {@link MonitorLogSaveReq}（操作日志传 type=0、{@code
 * content.logSubType=operationLog}）后调用本接口，由实现方负责日志落库。
 *
 * <p>{@code monitor_log} 表位于业务库，落库数据源按当前请求的 {@code X-Project-No} 解析，调用方无需关心。
 */
public interface OpenMonitorLogApi {

    /**
     * 保存监控日志
     *
     * @param req 监控日志保存请求
     */
    void saveLog(MonitorLogSaveReq req);
}
