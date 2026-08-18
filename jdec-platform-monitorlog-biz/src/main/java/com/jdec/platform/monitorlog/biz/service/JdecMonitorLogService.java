package com.jdec.platform.monitorlog.biz.service;

import cn.hutool.core.bean.BeanUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jdec.platform.config.api.SysConfigCategoryApi;
import com.jdec.platform.config.api.SysRoleApi;
import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.dto.response.RoleConfigDetailResp;
import com.jdec.platform.config.api.dto.response.SysConfigItemTreeResp;
import com.jdec.platform.config.api.dto.response.UserDetailResp;
import com.jdec.platform.monitorlog.api.MonitorLogApi;
import com.jdec.platform.monitorlog.api.dto.request.*;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogPageListResp;
import com.jdec.platform.monitorlog.biz.entity.*;
import com.jdec.platform.monitorlog.biz.mapper.*;
import com.jdec.platform.shared.datasource.*;
import com.jdec.platform.shared.model.PageResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class JdecMonitorLogService implements MonitorLogApi {

    private static final String MONITOR_LOG_TYPE_CATEGORY = "monitorLogType";

    private final JdecMonitorMessageLogMapper jdecMonitorMessageLogMapper;

    private final JdecMonitorControlLogMapper jdecMonitorControlLogMapper;

    private final JdecMonitorSystemLogMapper jdecMonitorSystemLogMapper;

    private final JdecMonitorMultiTableMapper jdecMonitorMultiTableMapper;

    private final ProjectProperties projectProperties;

    private final SysConfigCategoryApi sysConfigCategoryApi;

    private static final Set<String> BASE_LOG_TYPES =
            Set.of("loginin", "loginout", "changerole", "resetuser", "changesubject");
    private static final Set<String> MOCK_LOG_TYPES = Set.of("mockuser", "mockchangerole");

    private final SysUserApi sysUserApi;

    private final SysRoleApi sysRoleApi;

    private final ObjectMapper objectMapper;

    @Override
    public PageResult<MonitorLogPageListResp> queryPageList(
            MonitorLogPageListReq request, String projectNo) {
        String keyWord = StringUtils.hasText(request.getKeyWord()) ? request.getKeyWord() : "";

        List<SysConfigItemTreeResp> configItems =
                sysConfigCategoryApi.getItemsByCategoryAlias(MONITOR_LOG_TYPE_CATEGORY);

        Map<String, String> valueToLabelMap = new HashMap<>(8);
        if (configItems != null) {
            for (SysConfigItemTreeResp item : configItems) {
                if (item == null
                        || !StringUtils.hasText(item.getValue())
                        || !StringUtils.hasText(item.getLabel())) {
                    continue;
                }
                valueToLabelMap.put(item.getValue(), item.getLabel());
            }
        }

        String reqLogType = request.getLogType();
        String logType = StringUtils.hasText(reqLogType) ? reqLogType : "";

        String startTime =
                StringUtils.hasText(request.getStartTime())
                        ? request.getStartTime() + " 00:00:00"
                        : null;
        String endTime =
                StringUtils.hasText(request.getEndTime())
                        ? request.getEndTime() + " 23:59:59"
                        : null;
        Long offset = (request.getPageNum() - 1) * request.getPageSize();

        String dataSourceKey = projectProperties.getDataSourceKey(projectNo);
        DataSourceContextHolder.set(dataSourceKey);
        List<JdecMonitorLogBase> logList;
        long total;
        try {
            total =
                    jdecMonitorMultiTableMapper.countPageListWithMultiTable(
                            startTime, endTime, keyWord, logType);
            if (total > 0) {
                logList =
                        jdecMonitorMultiTableMapper.queryPageListWithMultiTable(
                                startTime,
                                endTime,
                                offset,
                                request.getPageSize(),
                                keyWord,
                                logType);
            } else {
                logList = List.of();
            }
        } finally {
            DataSourceContextHolder.clear();
        }

        List<MonitorLogPageListResp> respList =
                logList.stream()
                        .map(
                                logItem -> {
                                    MonitorLogPageListResp resp =
                                            BeanUtil.copyProperties(
                                                    logItem, MonitorLogPageListResp.class);
                                    String rawLogType = resp.getLogType();
                                    if (StringUtils.hasText(rawLogType)) {
                                        resp.setLogTypeLabel(valueToLabelMap.get(rawLogType));
                                    }
                                    return resp;
                                })
                        .toList();

        return PageResult.of(request.getPageNum(), request.getPageSize(), total, respList);
    }

    /**
     * 记录订单运行日志
     *
     * @param req 监控日志请求参数
     */
    @Override
    public void recordOrderRunLog(MonitorControlLogReq req, String projectNo) {
        boolean isAutoApproval = req.getApprovalType() != null && req.getApprovalType() == 0;
        if (!isAutoApproval) {
            populateUserAndRoleInfo(req);
        }
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
        boolean isAutoApproval = req.getApprovalType() != null && req.getApprovalType() == 0;
        if (!isAutoApproval) {
            populateUserAndRoleInfo(req);
        }
        populateMessageReceiversDetails(req);
        validateMessageLogReq(req);
        JdecMonitorMessageLog logItem = BeanUtil.toBean(req, JdecMonitorMessageLog.class);
        applyDefaults(logItem);

        String dataSourceKey = projectProperties.getDataSourceKey(projectNo);
        DataSourceContextHolder.set(dataSourceKey);
        jdecMonitorMessageLogMapper.insert(logItem);
        DataSourceContextHolder.clear();
    }

    /**
     * 根据logRemark中的messageReceivers用户ID列表，查询用户详情并填充到usersDetails字段
     *
     * @param req 监控消息日志请求参数
     */
    private void populateMessageReceiversDetails(MonitorMessageLogReq req) {
        if (!StringUtils.hasText(req.getLogRemark())) {
            return;
        }
        try {
            JsonNode rootNode = objectMapper.readTree(req.getLogRemark());
            if (!rootNode.has("messageReceivers") || rootNode.get("messageReceivers").isNull()) {
                return;
            }
            JsonNode receiversNode = rootNode.get("messageReceivers");
            if (!receiversNode.isArray()) {
                return;
            }
            List<Long> receiverIds = new ArrayList<>();
            for (JsonNode idNode : receiversNode) {
                if (idNode.canConvertToLong()) {
                    receiverIds.add(idNode.asLong());
                }
            }
            if (receiverIds.isEmpty()) {
                return;
            }
            ArrayNode usersDetailsNode = objectMapper.createArrayNode();
            for (Long userId : receiverIds) {
                UserDetailResp userDetail = sysUserApi.getUserDetailByUserId(userId);
                if (userDetail != null) {
                    ObjectNode userNode = objectMapper.createObjectNode();
                    userNode.put("UserName", userDetail.getUserName());
                    userNode.put("UserNumber", userDetail.getWorkNumber());
                    userNode.put("UserAvatar", userDetail.getUserAvatar());
                    usersDetailsNode.add(userNode);
                }
            }
            if (rootNode instanceof ObjectNode) {
                ((ObjectNode) rootNode).set("usersDetails", usersDetailsNode);
                req.setLogRemark(objectMapper.writeValueAsString(rootNode));
            }
        } catch (Exception e) {
            log.warn("解析logRemark并填充usersDetails失败，logRemark={}", req.getLogRemark(), e);
        }
    }

    /**
     * 记录系统操作日志
     *
     * @param req 监控日志请求参数
     */
    @Override
    public void recordSystemControlLog(MonitorSystemLogReq req, String projectNo) {
        populateUserAndRoleInfo(req);
        populateMockUserInfo(req);
        buildSystemLogContent(req);
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
        populateUserAndRoleInfo(req);
        populateMockUserInfo(req);
        validateSystemNavLogReq(req);
        JdecMonitorSystemLog logItem = BeanUtil.toBean(req, JdecMonitorSystemLog.class);
        applyDefaults(logItem);

        String dataSourceKey = projectProperties.getDataSourceKey(projectNo);
        DataSourceContextHolder.set(dataSourceKey);
        jdecMonitorSystemLogMapper.insert(logItem);
        DataSourceContextHolder.clear();
    }

    /**
     * 根据loginUserId和loginRoleId自动填充用户和角色信息
     *
     * @param req 监控日志请求参数
     */
    private void populateUserAndRoleInfo(MonitorBaseLogReq req) {
        if (req.getLoginUserId() == null || req.getLoginUserId() <= 0) {
            throw new IllegalArgumentException("登录用户ID(loginUserId)不能为空且必须大于0");
        }
        if (req.getLoginRoleId() == null || req.getLoginRoleId() <= 0) {
            throw new IllegalArgumentException("登录角色ID(loginRoleId)不能为空且必须大于0");
        }

        UserDetailResp userDetail = sysUserApi.getUserDetailByUserId(req.getLoginUserId());
        if (userDetail != null) {
            req.setLoginUserName(userDetail.getUserName());
            req.setLoginAvater(userDetail.getUserAvatar());
            req.setLoginUserNumber(userDetail.getWorkNumber());
            req.setLoginUserDeptName(userDetail.getDepartName());
            req.setLoginUserJobName(userDetail.getPositionName());
        }

        RoleConfigDetailResp roleDetail = sysRoleApi.getRoleConfigDetail(req.getLoginRoleId());
        if (roleDetail != null) {
            req.setLoginRoleName(roleDetail.getRoleName());
        }
    }

    /**
     * 根据mockUserId自动填充模拟用户信息
     *
     * @param req 监控日志请求参数
     */
    private void populateMockUserInfo(MonitorBaseLogReq req) {
        if (req.getMockFlag() == null || req.getMockFlag() != 1) {
            return;
        }
        if (req.getMockUserId() == null || req.getMockUserId() <= 0) {
            return;
        }

        UserDetailResp mockUserDetail = sysUserApi.getUserDetailByUserId(req.getMockUserId());
        if (mockUserDetail != null) {
            req.setMockUserName(mockUserDetail.getUserName());
            req.setMockUserAvater(mockUserDetail.getUserAvatar());
            req.setMockUserNumber(mockUserDetail.getWorkNumber());
        }
    }

    /**
     * 根据logType构建系统日志内容
     *
     * @param req 监控系统日志请求
     */
    private void buildSystemLogContent(MonitorSystemLogReq req) {
        if (!StringUtils.hasText(req.getLogType())) {
            return;
        }
        if (StringUtils.hasText(req.getLogContent())) {
            return;
        }

        String logType = req.getLogType();
        if (!BASE_LOG_TYPES.contains(logType) && !MOCK_LOG_TYPES.contains(logType)) {
            return;
        }

        String systemName = StringUtils.hasText(req.getSystemName()) ? req.getSystemName() : "";
        String ip = StringUtils.hasText(req.getIp()) ? req.getIp() : "";
        String loginUserName =
                StringUtils.hasText(req.getLoginUserName()) ? req.getLoginUserName() : "";
        String loginUserNumber =
                StringUtils.hasText(req.getLoginUserNumber()) ? req.getLoginUserNumber() : "";
        String loginRoleName =
                StringUtils.hasText(req.getLoginRoleName()) ? req.getLoginRoleName() : "";
        String mockUserName =
                StringUtils.hasText(req.getMockUserName()) ? req.getMockUserName() : "";
        String mockUserNumber =
                StringUtils.hasText(req.getMockUserNumber()) ? req.getMockUserNumber() : "";
        Integer mockFlug = req.getMockFlag() != null ? req.getMockFlag() : Integer.valueOf(0);
        String subjectName = StringUtils.hasText(req.getSubjectName()) ? req.getSubjectName() : "";

        String content =
                switch (logType) {
                    case "loginin" -> "登录" + systemName + "系统 [ip：" + ip + "]";
                    case "loginout" -> "退出" + systemName + "系统 [ip：" + ip + "]";
                    case "changerole" -> "切换角色：" + loginRoleName;
                    case "mockuser" ->
                            "切换身份：" + loginUserName + "(" + loginUserNumber + ")  " + loginRoleName;
                    case "mockchangerole" ->
                            "切换角色："
                                    + loginUserName
                                    + "("
                                    + loginUserNumber
                                    + ") 以 "
                                    + mockUserName
                                    + "("
                                    + mockUserNumber
                                    + ")的身份切换角色： "
                                    + loginRoleName;
                    case "resetuser" ->
                            "重置用户："
                                    + loginUserName
                                    + "("
                                    + loginUserNumber
                                    + ")重置角色："
                                    + loginRoleName;
                    case "changesubject" ->
                            "切换主体：["
                                    + subjectName
                                    + "]，用户："
                                    + loginUserName
                                    + "("
                                    + loginUserNumber
                                    + ")，角色：["
                                    + loginRoleName
                                    + "]";
                    default -> {
                        String operator =
                                (mockFlug == 1 && StringUtils.hasText(mockUserName))
                                        ? mockUserName + "(模拟)"
                                        : StringUtils.hasText(loginUserName) ? loginUserName : "";
                        yield operator + " - 系统操作";
                    }
                };
        req.setLogContent(content);
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
        if (req.getApprovalType() != null
                && (req.getApprovalType() < 0 || req.getApprovalType() > 1)) {
            throw new IllegalArgumentException("审批类型(approvalType)只能为0或1");
        }
        boolean isAutoApproval = req.getApprovalType() != null && req.getApprovalType() == 0;
        if (!isAutoApproval) {
            if (req.getLoginUserId() == null || req.getLoginUserId() <= 0) {
                throw new IllegalArgumentException("登录用户ID(loginUserId)不能为空且必须大于0");
            }
            if (req.getLoginRoleId() == null || req.getLoginRoleId() <= 0) {
                throw new IllegalArgumentException("登录角色ID(loginRoleId)不能为空且必须大于0");
            }
        }
        if (StringUtils.hasText(req.getIp()) && req.getIp().length() > 20) {
            throw new IllegalArgumentException("IP地址(ip)长度不能超过20字符");
        }
        if (StringUtils.hasText(req.getModuleName()) && req.getModuleName().length() > 100) {
            throw new IllegalArgumentException("模块名称(moduleName)长度不能超过100字符");
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
        if (req.getLogContent() != null && req.getLogContent().length() > 65535) {
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
        if (!StringUtils.hasText(req.getLogContent())) {
            throw new IllegalArgumentException("日志内容(logContent)不能为空");
        }
        if (req.getLogContent().length() > 65535) {
            throw new IllegalArgumentException("日志内容(logContent)长度不能超过65535字符");
        }
        if (StringUtils.hasText(req.getIp()) && req.getIp().length() > 20) {
            throw new IllegalArgumentException("IP地址(ip)长度不能超过20字符");
        }
        if (StringUtils.hasText(req.getModuleName()) && req.getModuleName().length() > 100) {
            throw new IllegalArgumentException("模块名称(moduleName)长度不能超过100字符");
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
        String type = StringUtils.hasText(logType) ? logType : "operationLog";
        String dataSourceKey = projectProperties.getDataSourceKey(projectNo);
        DataSourceContextHolder.set(dataSourceKey);
        List<JdecMonitorControlLog> logList =
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
        String type = "operationLog";
        String dataSourceKey = projectProperties.getDataSourceKey(projectNo);
        DataSourceContextHolder.set(dataSourceKey);
        List<JdecMonitorControlLog> logList =
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
        List<JdecMonitorControlLog> logList =
                jdecMonitorMultiTableMapper.querySystemControlLogByModuleIdAndDataId(
                        moduleId, dataId);
        DataSourceContextHolder.clear();
        return logList.stream()
                .map(item -> BeanUtil.copyProperties(item, MonitorLogPageListResp.class))
                .toList();
    }
}
