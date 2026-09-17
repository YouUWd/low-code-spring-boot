package com.jdec.platform.data.api;

import com.jdec.platform.data.api.dto.request.MonitorLogQueryReq;
import com.jdec.platform.data.api.dto.request.MonitorLogSaveReq;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.MonitorLogResp;

/**
 * 监控日志 API 接口
 *
 * <p>三种日志类型（操作日志 / 消息日志 / 系统日志）共用 {@code monitor_log} 一张表，类型独有字段以 JSON 形式落在 {@code approve}、{@code
 * content} 列中；表位于项目业务库，按当前请求上下文的项目编码（{@code X-Project-No} 请求头）动态切换数据源。
 */
public interface MonitorLogApi {

    /**
     * 保存监控日志
     *
     * <p>按 {@code type} 分发到对应的保存处理器，处理器负责把 {@code approve}、{@code content} 等对象序列化为 JSON
     * 字符串写入实体，最终入库。
     *
     * @param req 监控日志保存请求
     */
    void saveLog(MonitorLogSaveReq req);

    /**
     * 分页查询监控日志
     *
     * <p>支持记录时间范围、日志类型筛选与关键词模糊（同时作用于 {@code content} 与 {@code remark}），按记录时间倒序； 返回前将 {@code
     * approve}、{@code content} 等 JSON 字符串反序列化为对象。
     *
     * @param req 分页查询请求
     * @return 监控日志分页数据
     */
    DataPage<MonitorLogResp> queryLogPage(MonitorLogQueryReq req);
}
