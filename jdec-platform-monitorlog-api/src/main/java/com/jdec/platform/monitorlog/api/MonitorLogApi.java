package com.jdec.platform.monitorlog.api;

import com.jdec.platform.monitorlog.api.dto.request.*;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogPageListResp;
import com.jdec.platform.shared.model.PageResult;
import java.sql.SQLException;
import java.util.List;

/** 监控日志 Service */
public interface MonitorLogApi {
    /** 分页查询监控日志 */
    PageResult<MonitorLogPageListResp> queryPageList(MonitorLogPageListReq req);

    /** 记录消息日志 */
    void recordMessageLog(MonitorMessageLogReq req) throws SQLException;

    /** 记录系统操作日志 形如：登录ipd系统 [ip：126.214.120.01] */
    void recordSystemControlLog(MonitorSystemLogReq req) throws SQLException;

    /** 记录系统导航日志 */
    void recordSystemNavLog(MonitorSystemNavLogReq req) throws SQLException;

    /*记录业务单据变化日志*/
    void recordOrderRunLog(MonitorControlLogReq req) throws SQLException;

    /** 根据moduleId + orderId 查询多条日志记录（按时间倒序） */
    List<MonitorLogPageListResp> queryByModuleIdAndOrderId(
            Long moduleId, String orderId, String logType);

    /** 根据menuId + dataId 查询单条最新日志记录 */
    MonitorLogPageListResp queryByMenuIdAndDataId(Long menuId, Long dataId, String logType);
}
