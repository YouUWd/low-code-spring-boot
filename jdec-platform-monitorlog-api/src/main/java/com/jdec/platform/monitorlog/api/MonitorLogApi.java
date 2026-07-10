package com.jdec.platform.monitorlog.api;

import com.jdec.platform.monitorlog.api.dto.request.MonitorLogPageListReq;
import com.jdec.platform.monitorlog.api.dto.request.MonitorLogReq;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogPageListResp;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogResp;
import java.util.List;

/** 监控日志 Service */
public interface MonitorLogApi {
    /** 分页查询监控日志 */
    List<MonitorLogPageListResp> queryPageList(MonitorLogPageListReq req);

    /** 记录监控日志 */
    void record(MonitorLogReq req);

    /** 删除监控日志 */
    void delete(Long id);

    /** 获取监控日志详情 */
    MonitorLogResp get(Long id);
}
