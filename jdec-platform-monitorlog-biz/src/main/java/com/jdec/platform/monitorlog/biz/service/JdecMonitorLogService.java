package com.jdec.platform.monitorlog.biz.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jdec.platform.config.api.dto.response.RoleConfigDetailResp;
import com.jdec.platform.config.api.dto.response.UserDetailResp;
import com.jdec.platform.monitorlog.api.MonitorLogApi;
import com.jdec.platform.monitorlog.api.client.MonitorCenterApiClient;
import com.jdec.platform.monitorlog.api.dto.request.MonitorLogPageListReq;
import com.jdec.platform.monitorlog.api.dto.request.MonitorLogReq;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogPageListResp;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogResp;
import com.jdec.platform.monitorlog.biz.entity.JdecMonitorLogBase;
import com.jdec.platform.monitorlog.biz.entity.JdecMonitorMessageLog;
import com.jdec.platform.monitorlog.biz.entity.JdecMonitorOperationLog;
import com.jdec.platform.monitorlog.biz.entity.JdecMonitorSystemLog;
import com.jdec.platform.monitorlog.biz.mapper.JdecMonitorMessageLogMapper;
import com.jdec.platform.monitorlog.biz.mapper.JdecMonitorOperationLogMapper;
import com.jdec.platform.monitorlog.biz.mapper.JdecMonitorSystemLogMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.model.ApiResponse;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 监控日志服务实现 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.MonitorLog)
public class JdecMonitorLogService implements MonitorLogApi {

    private final JdecMonitorMessageLogMapper jdecMonitorMessageLogMapper;
    private final JdecMonitorSystemLogMapper jdecMonitorSystemLogMapper;
    private final JdecMonitorOperationLogMapper jdecMonitorOperationLogMapper;
    private final MonitorCenterApiClient monitorCenterApiClient;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired private SqlSessionTemplate sqlSessionTemplate;

    /** 日志类型常量 */
    private static final String LOG_TYPE_MESSAGE = "message-log";

    private static final String LOG_TYPE_OPERATION = "operation-log";
    private static final String LOG_TYPE_SYSTEM = "system-log";

    /**
     * 查询监控日志列表
     *
     * @param request 查询请求
     * @return 日志列表
     */
    @Override
    public List<MonitorLogPageListResp> queryPageList(MonitorLogPageListReq request) {
        // 解析时间
        LocalDate startDate = LocalDate.parse(request.getStartTime(), DATE_FORMATTER);
        LocalDate endDate = LocalDate.parse(request.getEndTime(), DATE_FORMATTER);
        LocalDateTime startDateTime = LocalDateTime.of(startDate, LocalTime.MIN);
        LocalDateTime endDateTime = LocalDateTime.of(endDate, LocalTime.MAX);

        // 根据日志类型选择查询
        String logType = request.getLogType();
        List<JdecMonitorLogBase> logs = new ArrayList<>();

        if (LOG_TYPE_MESSAGE.equalsIgnoreCase(logType)) {
            logs.addAll(
                    queryMessageLogs(
                                    request.getPage(),
                                    request.getPageSize(),
                                    request,
                                    startDateTime,
                                    endDateTime)
                            .getRecords());
        } else if (LOG_TYPE_SYSTEM.equalsIgnoreCase(logType)) {
            logs.addAll(
                    querySystemLogs(
                                    request.getPage(),
                                    request.getPageSize(),
                                    request,
                                    startDateTime,
                                    endDateTime)
                            .getRecords());
        } else if (LOG_TYPE_OPERATION.equalsIgnoreCase(logType)) {
            logs.addAll(
                    queryOperationLogs(
                                    request.getPage(),
                                    request.getPageSize(),
                                    request,
                                    startDateTime,
                                    endDateTime)
                            .getRecords());
        } else {
            // 未指定类型或类型为空，查询所有类型并合并排序
            logs =
                    queryAllLogs(
                            request.getPage(),
                            request.getPageSize(),
                            request,
                            startDateTime,
                            endDateTime);
        }

        // 转换为响应DTO
        return logs.stream().map(this::convertToResp).collect(Collectors.toList());
    }

    /** 查询消息日志 */
    private IPage<JdecMonitorMessageLog> queryMessageLogs(
            long pageNum,
            long pageSize,
            MonitorLogPageListReq request,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime) {
        Page<JdecMonitorMessageLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<JdecMonitorMessageLog> wrapper =
                buildMessageLogQueryWrapper(request, startDateTime, endDateTime);
        return jdecMonitorMessageLogMapper.selectPage(page, wrapper);
    }

    /** 查询操作日志 */
    private IPage<JdecMonitorOperationLog> queryOperationLogs(
            long pageNum,
            long pageSize,
            MonitorLogPageListReq request,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime) {
        Page<JdecMonitorOperationLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<JdecMonitorOperationLog> wrapper =
                buildOperationLogQueryWrapper(request, startDateTime, endDateTime);
        return jdecMonitorOperationLogMapper.selectPage(page, wrapper);
    }

    /** 查询系统日志 */
    private IPage<JdecMonitorSystemLog> querySystemLogs(
            long pageNum,
            long pageSize,
            MonitorLogPageListReq request,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime) {
        Page<JdecMonitorSystemLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<JdecMonitorSystemLog> wrapper =
                buildSystemLogQueryWrapper(request, startDateTime, endDateTime);
        return jdecMonitorSystemLogMapper.selectPage(page, wrapper);
    }

    /** 查询所有类型日志并合并 */
    private List<JdecMonitorLogBase> queryAllLogs(
            long pageNum,
            long pageSize,
            MonitorLogPageListReq request,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime) {
        // 查询三个表的数据
        IPage<JdecMonitorMessageLog> messagePage =
                queryMessageLogs(pageNum, pageSize, request, startDateTime, endDateTime);
        IPage<JdecMonitorOperationLog> operationPage =
                queryOperationLogs(pageNum, pageSize, request, startDateTime, endDateTime);
        IPage<JdecMonitorSystemLog> systemPage =
                querySystemLogs(pageNum, pageSize, request, startDateTime, endDateTime);

        // 合并结果
        List<JdecMonitorLogBase> allLogs = new ArrayList<>();
        allLogs.addAll(messagePage.getRecords());
        allLogs.addAll(operationPage.getRecords());
        allLogs.addAll(systemPage.getRecords());

        // 按日志日期降序排序
        allLogs.sort(
                (a, b) -> {
                    LocalDateTime dateA = a.getLogDate();
                    LocalDateTime dateB = b.getLogDate();
                    return dateB.compareTo(dateA);
                });

        // 截取分页结果
        int startIndex = pageNum > 1 ? (int) ((pageNum - 1) * pageSize) : 0;
        int endIndex = Math.min(startIndex + (int) pageSize, allLogs.size());

        return allLogs.subList(startIndex, endIndex);
    }

    /** 构建消息日志查询条件 */
    private LambdaQueryWrapper<JdecMonitorMessageLog> buildMessageLogQueryWrapper(
            MonitorLogPageListReq request, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        return new LambdaQueryWrapper<JdecMonitorMessageLog>()
                .like(
                        StringUtils.hasText(request.getKeyWord()),
                        JdecMonitorMessageLog::getLogContent,
                        request.getKeyWord())
                .between(JdecMonitorMessageLog::getLogDate, startDateTime, endDateTime)
                .orderByDesc(JdecMonitorMessageLog::getLogDate);
    }

    /** 构建操作日志查询条件 */
    private LambdaQueryWrapper<JdecMonitorOperationLog> buildOperationLogQueryWrapper(
            MonitorLogPageListReq request, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        return new LambdaQueryWrapper<JdecMonitorOperationLog>()
                .like(
                        StringUtils.hasText(request.getKeyWord()),
                        JdecMonitorOperationLog::getLogContent,
                        request.getKeyWord())
                .between(JdecMonitorOperationLog::getLogDate, startDateTime, endDateTime)
                .orderByDesc(JdecMonitorOperationLog::getLogDate);
    }

    /** 构建系统日志查询条件 */
    private LambdaQueryWrapper<JdecMonitorSystemLog> buildSystemLogQueryWrapper(
            MonitorLogPageListReq request, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        return new LambdaQueryWrapper<JdecMonitorSystemLog>()
                .like(
                        StringUtils.hasText(request.getKeyWord()),
                        JdecMonitorSystemLog::getLogContent,
                        request.getKeyWord())
                .between(JdecMonitorSystemLog::getLogDate, startDateTime, endDateTime)
                .orderByDesc(JdecMonitorSystemLog::getLogDate);
    }

    /** 转换为响应DTO */
    private MonitorLogPageListResp convertToResp(JdecMonitorLogBase log) {

        // 使用 BeanUtil 进行属性复制
        MonitorLogPageListResp resp = BeanUtil.toBean(log, MonitorLogPageListResp.class);

        // 根据实体类型设置日志类型
        if (log instanceof JdecMonitorMessageLog) {
            resp.setLogType(LOG_TYPE_MESSAGE);
            resp.setLogTypeLabel("消息日志");
        } else if (log instanceof JdecMonitorOperationLog) {
            resp.setLogType(LOG_TYPE_OPERATION);
            resp.setLogTypeLabel("操作日志");
        } else if (log instanceof JdecMonitorSystemLog) {
            resp.setLogType(LOG_TYPE_SYSTEM);
            resp.setLogTypeLabel("系统日志");
        }

        // 设置角色信息
        try {
            resp.setLoginRole(
                    log.getLoginRole() != null ? Long.parseLong(log.getLoginRole()) : null);
            resp.setMockRole(log.getMockRole() != null ? Long.parseLong(log.getMockRole()) : null);
        } catch (NumberFormatException e) {
            // log.warn("角色ID转换失败", e);
        }
        resp.setRoleName(log.getRoleName());

        return resp;
    }

    @Override
    @Transactional
    public void record(MonitorLogReq req) throws SQLException {
        // 获取当前 MyBatis 使用的连接并打印
        //        Connection connection =
        //                sqlSessionTemplate
        //                        .getConfiguration()
        //                        .getEnvironment()
        //                        .getDataSource()
        //                        .getConnection();
        //
        //        System.out.println("========== MyBatis 当前连接的数据库地址 ==========");
        //        System.out.println(connection.getMetaData().getURL());
        //        System.out.println("===============================================");

        if (req.getLoginUserId() == null || req.getLoginUserId() == 0L) {
            UserDetailResp userDetail = new UserDetailResp();
            ApiResponse<UserDetailResp> userDetailResp =
                    monitorCenterApiClient.getUserDetailByUserId(req.getLoginUserId());
            if (userDetailResp.getStatus() != 200) {
                throw new IllegalArgumentException("获取用户详情失败");
            }
            userDetail = userDetailResp.getData();
        }
        RoleConfigDetailResp roleConfigDetail = new RoleConfigDetailResp();
        if (req.getMockRole() == null || req.getMockRole() == 0L) {
            ApiResponse<RoleConfigDetailResp> roleConfigDetailResp =
                    monitorCenterApiClient.getRoleConfigDetail(req.getLoginRole());
            if (roleConfigDetailResp.getStatus() != 200) {
                throw new IllegalArgumentException("获取角色配置详情失败");
            }
            roleConfigDetail = roleConfigDetailResp.getData();
        }

        RoleConfigDetailResp mockRoleConfigDetail = new RoleConfigDetailResp();
        if (req.getMockRole() == null || req.getMockRole() == 0L) {
            ApiResponse<RoleConfigDetailResp> mockRoleConfigDetailResp =
                    monitorCenterApiClient.getRoleConfigDetail(req.getMockRole());
            if (mockRoleConfigDetailResp.getStatus() != 200) {
                throw new IllegalArgumentException("获取模拟角色配置详情失败");
            }
            mockRoleConfigDetail = mockRoleConfigDetailResp.getData();
        }
        if (req.getId() == null || req.getId() == 0L) {

            if ("system-log".equals(req.getLogType())) {
                JdecMonitorSystemLog log = BeanUtil.toBean(req, JdecMonitorSystemLog.class);
                log.setRoleName(roleConfigDetail.getRoleName());
                jdecMonitorSystemLogMapper.insert(log);
            } else if ("operation-log".equals(req.getLogType())) {
                JdecMonitorOperationLog log = BeanUtil.toBean(req, JdecMonitorOperationLog.class);
                jdecMonitorOperationLogMapper.insert(log);
            } else if ("message-log".equals(req.getLogType())) {
                JdecMonitorMessageLog log = BeanUtil.toBean(req, JdecMonitorMessageLog.class);
                jdecMonitorMessageLogMapper.insert(log);
            }
        } else {
            if ("system-log".equals(req.getLogType())) {
                JdecMonitorSystemLog log = BeanUtil.toBean(req, JdecMonitorSystemLog.class);
                jdecMonitorSystemLogMapper.updateById(log);
            } else if ("operation-log".equals(req.getLogType())) {
                JdecMonitorOperationLog log = BeanUtil.toBean(req, JdecMonitorOperationLog.class);
                jdecMonitorOperationLogMapper.updateById(log);
            } else if ("message-log".equals(req.getLogType())) {
                JdecMonitorMessageLog log = BeanUtil.toBean(req, JdecMonitorMessageLog.class);
                jdecMonitorMessageLogMapper.updateById(log);
            }
        }
    }

    @Override
    public MonitorLogResp get(Long id, String logType) {
        if (LOG_TYPE_MESSAGE.equals(logType)) {
            JdecMonitorMessageLog messageLog = jdecMonitorMessageLogMapper.selectById(id);
            if (messageLog != null) {
                return convertToMonitorLogResp(messageLog);
            }
        } else if (LOG_TYPE_OPERATION.equals(logType)) {
            JdecMonitorOperationLog operationLog = jdecMonitorOperationLogMapper.selectById(id);
            if (operationLog != null) {
                return convertToMonitorLogResp(operationLog);
            }
        } else if (LOG_TYPE_SYSTEM.equals(logType)) {
            JdecMonitorSystemLog systemLog = jdecMonitorSystemLogMapper.selectById(id);
            if (systemLog != null) {
                return convertToMonitorLogResp(systemLog);
            }
        }
        return null;
    }

    /** 将日志实体转换为 MonitorLogResp */
    private MonitorLogResp convertToMonitorLogResp(JdecMonitorLogBase log) {
        MonitorLogResp resp = BeanUtil.toBean(log, MonitorLogResp.class);

        // 字段映射处理
        resp.setLogContent(log.getLogContent());
        resp.setLogRemark(log.getLogRemark());
        resp.setIp(log.getIp());

        // 角色ID类型转换（String -> Long）
        try {
            resp.setLoginRole(
                    log.getLoginRole() != null ? Long.parseLong(log.getLoginRole()) : null);
            resp.setMockRole(log.getMockRole() != null ? Long.parseLong(log.getMockRole()) : null);
        } catch (NumberFormatException e) {
            // log.warn("角色ID转换失败", e);
        }

        return resp;
    }
}
