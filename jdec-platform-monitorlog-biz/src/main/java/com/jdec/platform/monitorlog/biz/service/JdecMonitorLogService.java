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
import com.jdec.platform.monitorlog.biz.entity.JdecMonitorSystemLog;
import com.jdec.platform.monitorlog.biz.mapper.JdecMonitorSystemLogMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.model.ApiResponse;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 监控日志服务实现 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.MonitorLog)
public class JdecMonitorLogService implements MonitorLogApi {

    private final JdecMonitorSystemLogMapper jdecMonitorSystemLogMapper;
    private final MonitorCenterApiClient monitorCenterApiClient;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String LOG_TYPE_MESSAGE = "message-log";
    private static final String LOG_TYPE_OPERATION = "operation-log";
    private static final String LOG_TYPE_SYSTEM = "system-log";

    @Override
    public List<MonitorLogPageListResp> queryPageList(MonitorLogPageListReq request) {
        LocalDate startDate = LocalDate.parse(request.getStartTime(), DATE_FORMATTER);
        LocalDate endDate = LocalDate.parse(request.getEndTime(), DATE_FORMATTER);
        LocalDateTime startDateTime = LocalDateTime.of(startDate, LocalTime.MIN);
        LocalDateTime endDateTime = LocalDateTime.of(endDate, LocalTime.MAX);

        Page<JdecMonitorSystemLog> page = new Page<>(request.getPage(), request.getPageSize());
        LambdaQueryWrapper<JdecMonitorSystemLog> wrapper =
                buildQueryWrapper(request, startDateTime, endDateTime);
        IPage<JdecMonitorSystemLog> result = jdecMonitorSystemLogMapper.selectPage(page, wrapper);

        return result.getRecords().stream().map(this::convertToResp).collect(Collectors.toList());
    }

    private LambdaQueryWrapper<JdecMonitorSystemLog> buildQueryWrapper(
            MonitorLogPageListReq request, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        LambdaQueryWrapper<JdecMonitorSystemLog> wrapper =
                new LambdaQueryWrapper<JdecMonitorSystemLog>()
                        .like(
                                StringUtils.hasText(request.getKeyWord()),
                                JdecMonitorSystemLog::getLogContent,
                                request.getKeyWord())
                        .between(JdecMonitorSystemLog::getLogDate, startDateTime, endDateTime)
                        .orderByDesc(JdecMonitorSystemLog::getLogDate);
        if (StringUtils.hasText(request.getLogType())) {
            wrapper.eq(JdecMonitorSystemLog::getLogType, request.getLogType());
        }
        return wrapper;
    }

    private MonitorLogPageListResp convertToResp(JdecMonitorSystemLog log) {
        MonitorLogPageListResp resp = BeanUtil.toBean(log, MonitorLogPageListResp.class);

        resp.setLogType(log.getLogType());
        if (LOG_TYPE_MESSAGE.equals(log.getLogType())) {
            resp.setLogTypeLabel("消息日志");
        } else if (LOG_TYPE_OPERATION.equals(log.getLogType())) {
            resp.setLogTypeLabel("操作日志");
        } else if (LOG_TYPE_SYSTEM.equals(log.getLogType())) {
            resp.setLogTypeLabel("系统日志");
        }

        resp.setLoginRole(log.getLoginRoleId());
        resp.setMockRole(log.getMockRoleId());
        resp.setRoleName(log.getLoginRoleName());

        return resp;
    }

    @Override
    @Transactional
    public void record(MonitorLogReq req) throws SQLException {
        // 查询角色信息
        RoleConfigDetailResp roleConfigDetail = new RoleConfigDetailResp();
        if (req.getLoginRoleId() != null && req.getLoginRoleId() != 0L) {
            ApiResponse<RoleConfigDetailResp> roleConfigDetailResp =
                    monitorCenterApiClient.getRoleConfigDetail(req.getLoginRoleId());
            if (roleConfigDetailResp.getStatus() != 200) {
                throw new IllegalArgumentException("获取角色配置详情失败");
            }
            roleConfigDetail = roleConfigDetailResp.getData();
        }
        // 查询用户信息
        UserDetailResp userDetail = new UserDetailResp();
        if (req.getMockUserId() != null && req.getMockUserId() != 0L) {
            ApiResponse<UserDetailResp> userDetailResp =
                    monitorCenterApiClient.getUserDetailByUserId(req.getMockUserId());
            if (userDetailResp.getStatus() != 200) {
                throw new IllegalArgumentException("获取用户详情失败");
            }
            userDetail = userDetailResp.getData();
        }

        RoleConfigDetailResp mockRoleConfigDetail = new RoleConfigDetailResp();
        if (req.getMockRoleId() != null && req.getMockRoleId() != 0L) {
            ApiResponse<RoleConfigDetailResp> mockRoleConfigDetailResp =
                    monitorCenterApiClient.getRoleConfigDetail(req.getMockRoleId());
            if (mockRoleConfigDetailResp.getStatus() != 200) {
                throw new IllegalArgumentException("获取模拟角色配置详情失败");
            }
            mockRoleConfigDetail = mockRoleConfigDetailResp.getData();
        }

        JdecMonitorSystemLog log = BeanUtil.toBean(req, JdecMonitorSystemLog.class);
        log.setMockUserName(userDetail.getUserName());
        log.setLoginRoleName(roleConfigDetail.getRoleName());
        log.setMockRoleName(mockRoleConfigDetail.getRoleName());
        if (req.getId() == null || req.getId() == 0L) {
            jdecMonitorSystemLogMapper.insert(log);
        } else {
            jdecMonitorSystemLogMapper.updateById(log);
        }
    }

    @Override
    public MonitorLogResp get(Long id, String logType) {
        JdecMonitorSystemLog log = jdecMonitorSystemLogMapper.selectById(id);
        if (log != null) {
            MonitorLogResp resp = BeanUtil.toBean(log, MonitorLogResp.class);
            resp.setLogContent(log.getLogContent());
            resp.setLogRemark(log.getLogRemark());
            resp.setIp(log.getIp());
            return resp;
        }
        return null;
    }
}
