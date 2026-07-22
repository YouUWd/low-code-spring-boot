package com.jdec.platform.monitorlog.api;

import com.jdec.platform.monitorlog.api.dto.request.MonitorLogPageListReq;
import com.jdec.platform.monitorlog.api.dto.request.MonitorLogReq;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogPageListResp;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogResp;
import java.sql.SQLException;
import java.util.List;

/** 监控日志 Service */
public interface MonitorLogApi {
    /** 分页查询监控日志 */
    List<MonitorLogPageListResp> queryPageList(MonitorLogPageListReq req);

    /** 记录操作日志 形如：[系统设置-审批链配置]修改了配置 带remark 如：[审批链分类] :PFD\r\n[模块名称]:PFD->PFD-1 */
    void recordControlLog(MonitorLogReq req) throws SQLException;

    /** 记录审批链日志 形如：[元件管理-FHSQ2409230955401 V2] 元件审核已驳回 带remark 如：驳回原因：审批不通过.. */
    void recordApprovalChainLog(MonitorLogReq req) throws SQLException;

    /** 记录登录日志 形如：登录ipd系统 [ip：126.214.120.01] */
    void recordLoginLog(MonitorLogReq req) throws SQLException;

    /** 记录退出日志 形如：退出ipd系统 [ip：126.214.120.01] */
    void recordLogoutLog(MonitorLogReq req) throws SQLException;

    /** 记录导航日志 形如： [元件管理-列表] 进入列表页面 */
    void recordNavLog(MonitorLogReq req) throws SQLException;

    /** 记录切换模拟用户日志 形如：切换身份：肖国平 (J10006721) 超级管理员 */
    void recordMockUser(MonitorLogReq req) throws SQLException;

    /** 记录切换角色日志 形如：切换角色：元件管理审核 */
    void recordChaneRole(MonitorLogReq req) throws SQLException;

    /** 记录重置模拟用户日志 形如：重置模拟用户：管理员 */
    void recordMockReset(MonitorLogReq req) throws SQLException;

    /** 记录切换主题日志 形如：切换主题：[主题名称]，用户：[用户名]，角色：[角色名称] */
    void recordChangeSubject(Long userId, Long roleId, String subjectName) throws SQLException;

    /** 记录消息日志 */
    void recordMessageLog(MonitorLogReq req) throws SQLException;

    /** 获取监控日志详情 */
    MonitorLogResp get(Long id, String logType);
}
