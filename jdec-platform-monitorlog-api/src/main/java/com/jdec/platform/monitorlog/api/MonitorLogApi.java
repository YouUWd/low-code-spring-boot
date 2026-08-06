package com.jdec.platform.monitorlog.api;

import com.jdec.platform.monitorlog.api.dto.request.*;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogPageListResp;
import com.jdec.platform.shared.model.PageResult;
import java.sql.SQLException;
import java.util.List;

/** 监控日志 Service */
public interface MonitorLogApi {
    /** 分页查询监控日志 */
    PageResult<MonitorLogPageListResp> queryPageList(MonitorLogPageListReq req, String projectNo);

    /** 记录消息日志 */
    void recordMessageLog(MonitorMessageLogReq req, String projectNo) throws SQLException;

    /** 记录系统操作日志 形如：登录ipd系统 [ip：126.214.120.01] */
    void recordSystemControlLog(MonitorSystemLogReq req, String projectNo) throws SQLException;

    /** 记录系统导航日志 */
    void recordSystemNavLog(MonitorSystemNavLogReq req, String projectNo) throws SQLException;

    /*记录业务单据变化日志*/
    void recordOrderRunLog(MonitorControlLogReq req, String projectNo) throws SQLException;

    /** 根据moduleId + orderId 查询多条日志记录（按时间倒序） */
    List<MonitorLogPageListResp> queryByModuleIdAndOrderId(
            Long moduleId, String orderId, String logType, String projectNo);

    /** 根据menuId + dataId 查询单条最新日志记录 */
    MonitorLogPageListResp queryByMenuIdAndDataId(Long menuId, Long dataId, String projectNo);

    /** 根据moduleId + dataId 查询单条最新系统操作日志记录（唯一一条，按时间倒序） */
    MonitorLogPageListResp querySystemControlLogByModuleIdAndDataId(
            Long moduleId, Long dataId, String projectNo);
}
