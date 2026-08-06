package com.jdec.platform.monitorlog.biz.service;

import cn.hutool.core.bean.BeanUtil;
import com.jdec.platform.monitorlog.api.MonitorLogApi;
import com.jdec.platform.monitorlog.api.dto.request.*;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogPageListResp;
import com.jdec.platform.monitorlog.biz.entity.*;
import com.jdec.platform.monitorlog.biz.mapper.*;
import com.jdec.platform.shared.datasource.*;
import com.jdec.platform.shared.model.PageResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
// @DataSource(DataSourceConstants.MonitorLog)
public class JdecMonitorLogService implements MonitorLogApi {

    private final JdecMonitorMessageLogMapper jdecMonitorMessageLogMapper;

    private final JdecMonitorControlLogMapper jdecMonitorControlLogMapper;

    private final JdecMonitorSystemLogMapper jdecMonitorSystemLogMapper;

    private final JdecMonitorMultiTableMapper jdecMonitorMultiTableMapper;

    private final ProjectProperties projectProperties;

    @Override
    public PageResult<MonitorLogPageListResp> queryPageList(
            MonitorLogPageListReq request, String projectNo) {
        String keyWord = StringUtils.hasText(request.getKeyWord()) ? request.getKeyWord() : "";
        String logType =
                StringUtils.hasText(request.getLogType()) ? request.getLogType() : "controllog";
        String dataSourceKey = projectProperties.getDataSourceKey(projectNo);
        DataSourceContextHolder.set(dataSourceKey);
        List<JdecMonitorLogBase> logList =
                jdecMonitorMultiTableMapper.queryPageListWithMultiTable(
                        request.getLastTime(),
                        request.getLastId(),
                        request.getPageSize(),
                        keyWord,
                        logType);
        DataSourceContextHolder.clear();

        List<MonitorLogPageListResp> respList =
                logList.stream()
                        .map(
                                logItem ->
                                        BeanUtil.copyProperties(
                                                logItem, MonitorLogPageListResp.class))
                        .toList();

        return PageResult.of(
                request.getPageNum(), request.getPageSize(), (long) respList.size(), respList);
    }

    /**
     * 记录订单运行日志
     *
     * @param req 监控日志请求参数
     */
    @Override
    public void recordOrderRunLog(MonitorControlLogReq req, String projectNo) {
        validateControlLogReq(req);
        JdecMonitorControlLog logItem = BeanUtil.toBean(req, JdecMonitorControlLog.class);
        applyDefaults(logItem);
        String dataSourceKey = projectProperties.getDataSourceKey(projectNo);
        DataSourceContextHolder.set(dataSourceKey);
        jdecMonitorControlLogMapper.insert(logItem);
        DataSourceContextHolder.clear();
    }

    /**
     * 记录消息日志
     *
     * @param req 监控日志请求参数
     */
    @Override
    public void recordMessageLog(MonitorMessageLogReq req, String projectNo) {
        validateMessageLogReq(req);
        JdecMonitorMessageLog logItem = BeanUtil.toBean(req, JdecMonitorMessageLog.class);
        applyDefaults(logItem);

        String dataSourceKey = projectProperties.getDataSourceKey(projectNo);
        DataSourceContextHolder.set(dataSourceKey);
        jdecMonitorMessageLogMapper.insert(logItem);
        DataSourceContextHolder.clear();
    }

    /**
     * 记录系统操作日志
     *
     * @param req 监控日志请求参数
     */
    @Override
    public void recordSystemControlLog(MonitorSystemLogReq req, String projectNo) {
        validateSystemLogReq(req);
        JdecMonitorSystemLog logItem = BeanUtil.toBean(req, JdecMonitorSystemLog.class);
        applyDefaults(logItem);

        String dataSourceKey = projectProperties.getDataSourceKey(projectNo);
        DataSourceContextHolder.set(dataSourceKey);
        jdecMonitorSystemLogMapper.insert(logItem);
        DataSourceContextHolder.clear();
    }

    /**
     * 记录系统导航日志
     *
     * @param req 监控日志请求参数
     */
    @Override
    public void recordSystemNavLog(MonitorSystemNavLogReq req, String projectNo) {
        validateSystemNavLogReq(req);
        JdecMonitorSystemLog logItem = BeanUtil.toBean(req, JdecMonitorSystemLog.class);
        applyDefaults(logItem);
        // 构建用户信息
        //        if (req.getMockFlag() == 0) {
        //            String userInfo =
        //                    USER_INFO_TEMPLATE
        //                            .replace("{avatar}", userDetail.getUserAvatar())
        //                            .replace("{userName}", userDetail.getUserName())
        //                            .replace("{workNumber}", userDetail.getWorkNumber());
        //            log.setUserInfo(userInfo);
        //        } else {
        //            String mockUserInfo =
        //                    MOCK_USER_INFO_TEMPLATE
        //                            .replace("{userAvatar}", userDetail.getUserAvatar())
        //                            .replace("{userName}", userDetail.getUserName())
        //                            .replace("{userWorkNumber}", userDetail.getWorkNumber())
        //                            .replace("{mockAvatar}", mockUserDetail.getUserAvatar())
        //                            .replace("{mockUserName}", mockUserDetail.getUserName())
        //                            .replace("{mockWorkNumber}", mockUserDetail.getWorkNumber());
        //            log.setUserInfo(mockUserInfo);
        //        }
        /*
        {
            "moduleName": "模块名称/功能名称/页面名称",
            "controlName": "操作名称/单据名称",
            "hrefUrl": "单据详情页/订单详情页/业务详情页/任务详情页",
            "workDesc":"操作描述"
        }
         */

        String dataSourceKey = projectProperties.getDataSourceKey(projectNo);
        DataSourceContextHolder.set(dataSourceKey);
        jdecMonitorSystemLogMapper.insert(logItem);
        DataSourceContextHolder.clear();
    }

    /**
     * 校验日志请求参数
     *
     * @param req 监控日志请求参数
     */
    private void validateControlLogReq(MonitorControlLogReq req) {
        if (req == null) {
            throw new IllegalArgumentException("日志请求参数不能为空");
        }
        if (req.getSubjectId() == null || req.getSubjectId() <= 0) {
            throw new IllegalArgumentException("主体ID(subjectId)不能为空且必须大于0");
        }
        if (req.getLoginUserId() == null || req.getLoginUserId() <= 0) {
            throw new IllegalArgumentException("登录用户ID(loginUserId)不能为空且必须大于0");
        }
        if (!StringUtils.hasText(req.getLogContent())) {
            throw new IllegalArgumentException("日志内容(logContent)不能为空");
        }
        if (!StringUtils.hasText(req.getLogRemark())) {
            throw new IllegalArgumentException("日志备注(logRemark)不能为空");
        }
        if (StringUtils.hasText(req.getIp()) && req.getIp().length() > 20) {
            throw new IllegalArgumentException("IP地址(ip)长度不能超过20字符");
        }
        if (StringUtils.hasText(req.getModuleName()) && req.getModuleName().length() > 100) {
            throw new IllegalArgumentException("模块名称(moduleName)长度不能超过100字符");
        }
        if (StringUtils.hasText(req.getOrderId()) && req.getOrderId().length() > 100) {
            throw new IllegalArgumentException("单据编号(orderId)长度不能超过100字符");
        }
        if (StringUtils.hasText(req.getControlName()) && req.getControlName().length() > 100) {
            throw new IllegalArgumentException("单据操作名称(controlName)长度不能超过100字符");
        }
        if (StringUtils.hasText(req.getBrowser()) && req.getBrowser().length() > 100) {
            throw new IllegalArgumentException("浏览器信息(browser)长度不能超过100字符");
        }
        if (StringUtils.hasText(req.getPlatForm()) && req.getPlatForm().length() > 100) {
            throw new IllegalArgumentException("平台信息(platForm)长度不能超过100字符");
        }
        if (StringUtils.hasText(req.getSystemName()) && req.getSystemName().length() > 100) {
            throw new IllegalArgumentException("系统名称(systemName)长度不能超过100字符");
        }
        if (req.getMockFlag() != null && (req.getMockFlag() < 0 || req.getMockFlag() > 1)) {
            throw new IllegalArgumentException("mock标记(mockFlag)只能为0或1");
        }
    }

    private void validateSystemLogReq(MonitorSystemLogReq req) {
        if (req == null) {
            throw new IllegalArgumentException("日志请求参数不能为空");
        }
        if (req.getSubjectId() == null || req.getSubjectId() <= 0) {
            throw new IllegalArgumentException("主体ID(subjectId)不能为空且必须大于0");
        }
        if (req.getLoginUserId() == null || req.getLoginUserId() <= 0) {
            throw new IllegalArgumentException("登录用户ID(loginUserId)不能为空且必须大于0");
        }
        if (req.getLogContent().length() > 65535) {
            throw new IllegalArgumentException("日志内容(logContent)长度不能超过65535字符");
        }
        if (StringUtils.hasText(req.getIp()) && req.getIp().length() > 20) {
            throw new IllegalArgumentException("IP地址(ip)长度不能超过20字符");
        }
        if (StringUtils.hasText(req.getBrowser()) && req.getBrowser().length() > 100) {
            throw new IllegalArgumentException("浏览器信息(browser)长度不能超过100字符");
        }
        if (StringUtils.hasText(req.getPlatForm()) && req.getPlatForm().length() > 100) {
            throw new IllegalArgumentException("平台信息(platForm)长度不能超过100字符");
        }
        if (StringUtils.hasText(req.getSystemName()) && req.getSystemName().length() > 100) {
            throw new IllegalArgumentException("系统名称(systemName)长度不能超过100字符");
        }
        if (req.getMockFlag() != null && (req.getMockFlag() < 0 || req.getMockFlag() > 1)) {
            throw new IllegalArgumentException("mock标记(mockFlag)只能为0或1");
        }
    }

    private void validateSystemNavLogReq(MonitorSystemNavLogReq req) {
        if (req == null) {
            throw new IllegalArgumentException("日志请求参数不能为空");
        }
        if (req.getSubjectId() == null || req.getSubjectId() <= 0) {
            throw new IllegalArgumentException("主体ID(subjectId)不能为空且必须大于0");
        }
        if (req.getLoginUserId() == null || req.getLoginUserId() <= 0) {
            throw new IllegalArgumentException("登录用户ID(loginUserId)不能为空且必须大于0");
        }
        if (!StringUtils.hasText(req.getLogContent())) {
            throw new IllegalArgumentException("日志内容(logContent)不能为空");
        }
        if (req.getLogContent().length() > 65535) {
            throw new IllegalArgumentException("日志内容(logContent)长度不能超过65535字符");
        }
        if (StringUtils.hasText(req.getIp()) && req.getIp().length() > 20) {
            throw new IllegalArgumentException("IP地址(ip)长度不能超过20字符");
        }
        if (StringUtils.hasText(req.getBrowser()) && req.getBrowser().length() > 100) {
            throw new IllegalArgumentException("浏览器信息(browser)长度不能超过100字符");
        }
        if (StringUtils.hasText(req.getPlatForm()) && req.getPlatForm().length() > 100) {
            throw new IllegalArgumentException("平台信息(platForm)长度不能超过100字符");
        }
        if (StringUtils.hasText(req.getSystemName()) && req.getSystemName().length() > 100) {
            throw new IllegalArgumentException("系统名称(systemName)长度不能超过100字符");
        }
        if (req.getMockFlag() != null && (req.getMockFlag() < 0 || req.getMockFlag() > 1)) {
            throw new IllegalArgumentException("mock标记(mockFlag)只能为0或1");
        }
    }

    private void validateMessageLogReq(MonitorMessageLogReq req) {
        if (req == null) {
            throw new IllegalArgumentException("日志请求参数不能为空");
        }
        if (req.getSubjectId() == null || req.getSubjectId() <= 0) {
            throw new IllegalArgumentException("主体ID(subjectId)不能为空且必须大于0");
        }
        if (req.getLoginUserId() == null || req.getLoginUserId() <= 0) {
            throw new IllegalArgumentException("登录用户ID(loginUserId)不能为空且必须大于0");
        }
        if (!StringUtils.hasText(req.getLogContent())) {
            throw new IllegalArgumentException("日志内容(logContent)不能为空");
        }
        if (req.getLogContent().length() > 65535) {
            throw new IllegalArgumentException("日志内容(logContent)长度不能超过65535字符");
        }
        if (!StringUtils.hasText(req.getLogRemark())) {
            throw new IllegalArgumentException("日志备注(logRemark)不能为空");
        }
        if (StringUtils.hasText(req.getIp()) && req.getIp().length() > 20) {
            throw new IllegalArgumentException("IP地址(ip)长度不能超过20字符");
        }
        if (StringUtils.hasText(req.getModuleName()) && req.getModuleName().length() > 100) {
            throw new IllegalArgumentException("模块名称(moduleName)长度不能超过100字符");
        }
        if (StringUtils.hasText(req.getOrderId()) && req.getOrderId().length() > 100) {
            throw new IllegalArgumentException("单据编号(orderId)长度不能超过100字符");
        }
        if (StringUtils.hasText(req.getControlName()) && req.getControlName().length() > 100) {
            throw new IllegalArgumentException("单据操作名称(controlName)长度不能超过100字符");
        }
        if (StringUtils.hasText(req.getBrowser()) && req.getBrowser().length() > 100) {
            throw new IllegalArgumentException("浏览器信息(browser)长度不能超过100字符");
        }
        if (StringUtils.hasText(req.getPlatForm()) && req.getPlatForm().length() > 100) {
            throw new IllegalArgumentException("平台信息(platForm)长度不能超过100字符");
        }
        if (StringUtils.hasText(req.getSystemName()) && req.getSystemName().length() > 100) {
            throw new IllegalArgumentException("系统名称(systemName)长度不能超过100字符");
        }
        if (req.getMockFlag() != null && (req.getMockFlag() < 0 || req.getMockFlag() > 1)) {
            throw new IllegalArgumentException("mock标记(mockFlag)只能为0或1");
        }
    }

    /**
     * 应用默认值
     *
     * @param logItem 日志实体
     */
    private void applyDefaults(JdecMonitorLogBase logItem) {
        if (logItem.getMockFlag() == null) {
            logItem.setMockFlag(0);
        }
        if (logItem.getMockUserId() == null) {
            logItem.setMockUserId(0L);
        }
        if (!StringUtils.hasText(logItem.getLoginUserDeptId())) {
            logItem.setLoginUserDeptId("0");
        }
        if (!StringUtils.hasText(logItem.getLoginUserJobId())) {
            logItem.setLoginUserJobId("0");
        }
        if (!StringUtils.hasText(logItem.getLoginUserName())) {
            logItem.setLoginUserName("");
        }
        if (!StringUtils.hasText(logItem.getLoginAvater())) {
            logItem.setLoginAvater("");
        }
        if (!StringUtils.hasText(logItem.getLoginUserNumber())) {
            logItem.setLoginUserNumber("");
        }
        if (!StringUtils.hasText(logItem.getLoginRoleName())) {
            logItem.setLoginRoleName("");
        }
        if (!StringUtils.hasText(logItem.getLoginUserDeptName())) {
            logItem.setLoginUserDeptName("");
        }
        if (!StringUtils.hasText(logItem.getLoginUserJobName())) {
            logItem.setLoginUserJobName("");
        }
        if (!StringUtils.hasText(logItem.getBrowser())) {
            logItem.setBrowser("chrome");
        }
        if (!StringUtils.hasText(logItem.getPlatForm())) {
            logItem.setPlatForm("windows");
        }
        if (!StringUtils.hasText(logItem.getLogRemark())) {
            logItem.setLogRemark("{}");
        }
    }

    @Override
    public List<MonitorLogPageListResp> queryByModuleIdAndOrderId(
            Long moduleId, String orderId, String logType, String projectNo) {
        if (moduleId == null || !StringUtils.hasText(orderId)) {
            throw new IllegalArgumentException("moduleId和orderId不能为空");
        }
        String type = StringUtils.hasText(logType) ? logType : "controllog";
        String dataSourceKey = projectProperties.getDataSourceKey(projectNo);
        DataSourceContextHolder.set(dataSourceKey);
        List<JdecMonitorLogBase> logList =
                jdecMonitorMultiTableMapper.queryByModuleIdAndOrderId(moduleId, orderId, type);
        DataSourceContextHolder.clear();
        return logList.stream()
                .map(item -> BeanUtil.copyProperties(item, MonitorLogPageListResp.class))
                .toList();
    }

    @Override
    public MonitorLogPageListResp queryByMenuIdAndDataId(
            Long menuId, Long dataId, String projectNo) {
        if (menuId == null || dataId == null) {
            throw new IllegalArgumentException("menuId和dataId不能为空");
        }
        String type = "controllog";
        String dataSourceKey = projectProperties.getDataSourceKey(projectNo);
        DataSourceContextHolder.set(dataSourceKey);
        List<JdecMonitorLogBase> logList =
                jdecMonitorMultiTableMapper.queryByMenuIdAndDataId(menuId, dataId, type);
        DataSourceContextHolder.clear();
        if (logList.isEmpty()) {
            return null;
        }
        return BeanUtil.copyProperties(logList.getFirst(), MonitorLogPageListResp.class);
    }

    @Override
    public List<MonitorLogPageListResp> querySystemControlLogByModuleIdAndDataId(
            Long moduleId, Long dataId, String projectNo) {
        if (moduleId == null || dataId == null) {
            throw new IllegalArgumentException("moduleId和dataId不能为空");
        }
        String dataSourceKey = projectProperties.getDataSourceKey(projectNo);
        DataSourceContextHolder.set(dataSourceKey);
        List<JdecMonitorLogBase> logList =
                jdecMonitorMultiTableMapper.querySystemControlLogByModuleIdAndDataId(
                        moduleId, dataId);
        DataSourceContextHolder.clear();
        return logList.stream()
                .map(item -> BeanUtil.copyProperties(item, MonitorLogPageListResp.class))
                .toList();
    }
}
