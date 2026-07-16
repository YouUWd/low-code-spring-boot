package com.jdec.platform.monitorlog.biz.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jdec.platform.config.api.dto.response.RoleConfigDetailResp;
import com.jdec.platform.config.api.dto.response.UserDetailResp;
import com.jdec.platform.monitorlog.api.MonitorLogApi;
import com.jdec.platform.monitorlog.api.client.MonitorLogApiClient;
import com.jdec.platform.monitorlog.api.dto.request.MonitorLogPageListReq;
import com.jdec.platform.monitorlog.api.dto.request.MonitorLogReq;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogPageListResp;
import com.jdec.platform.monitorlog.api.dto.response.MonitorLogResp;
import com.jdec.platform.monitorlog.biz.entity.JdecMonitorSystemLog;
import com.jdec.platform.monitorlog.biz.mapper.JdecMonitorSystemLogMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.model.ApiResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 监控日志服务实现 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.MonitorLog)
public class JdecMonitorLogService implements MonitorLogApi {

    private final JdecMonitorSystemLogMapper jdecMonitorSystemLogMapper;
    private final MonitorLogApiClient monitorLogApiClient;

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
            resp.setLogContent(formatOperationLogContent(log.getLogContent()));
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
    public void record(MonitorLogReq req) {

        if (LOG_TYPE_OPERATION.equals(req.getLogType())) {
            validateOperationLogContent(req.getLogContent());
        }

        UserDetailResp userDetail = new UserDetailResp();
        if (req.getLoginUserId() != null && req.getLoginUserId() != 0L) {
            userDetail = queryUserDetail(req.getLoginUserId());
        }

        RoleConfigDetailResp roleConfigDetail = new RoleConfigDetailResp();
        if (req.getLoginRoleId() != null && req.getLoginRoleId() != 0L) {
            roleConfigDetail = queryRoleConfigDetail(req.getLoginRoleId());
        }

        UserDetailResp mockUserDetail = new UserDetailResp();
        if (req.getMockUserId() != null && req.getMockUserId() != 0L) {
            mockUserDetail = queryUserDetail(req.getMockUserId());
        }

        RoleConfigDetailResp mockRoleConfigDetail = new RoleConfigDetailResp();
        if (req.getMockRoleId() != null && req.getMockRoleId() != 0L) {
            mockRoleConfigDetail = queryRoleConfigDetail(req.getMockRoleId());
        }

        JdecMonitorSystemLog log = BeanUtil.toBean(req, JdecMonitorSystemLog.class);
        if (req.getMockFlag() == 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("<span class=\"arco-table-td-content\">\n");
            sb.append("    <div class=\"flex\">\n");
            sb.append("        <span>\n");
            sb.append("          <div class=\"flex flex-items-center\"></div>\n");
            sb.append("        </span>\n");
            sb.append(
                    "        <div class=\"arco-image arco-image-with-footer-inner m-r-8px cursor-pointer\" style=\"width: 28px; height: 28px; border-radius: 50%;\">\n");
            sb.append("         <img class=\"arco-image-img\" src=\"")
                    .append(userDetail.getUserAvatar())
                    .append("\" style=\"width: 28px; height: 28px;\">\n");
            sb.append("        <!--v-if-->\n");
            sb.append("        <!--v-if-->\n");
            sb.append("       </div>\n");
            sb.append("        <span>\n");
            sb.append("          <span>{").append(userDetail.getUserName()).append("}</span>\n");
            sb.append("           <span class=\"c-#667085\">({")
                    .append(userDetail.getWorkNumber())
                    .append("})</span>\n");
            sb.append("        </span>\n");
            sb.append("    </div>\n");
            sb.append("</span>");
            log.setUserInfo(sb.toString());
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("<span class=\"arco-table-td-content\">\n");
            sb.append("    <div class=\"flex\">\n");
            sb.append("        <span>\n");
            sb.append("          <div class=\"flex flex-items-center\"></div>\n");
            sb.append("        </span>\n");
            sb.append(
                    "        <div class=\"arco-image arco-image-with-footer-inner m-r-8px cursor-pointer\" style=\"width: 28px; height: 28px; border-radius: 50%;\">\n");
            sb.append("         <img class=\"arco-image-img\" src=\"")
                    .append(userDetail.getUserAvatar())
                    .append("\" style=\"width: 28px; height: 28px;\"><!--v-if--><!--v-if-->\n");
            sb.append("       </div>\n");
            sb.append("        <span>\n");
            sb.append("          <span>{").append(userDetail.getUserName()).append("}</span>\n");
            sb.append("           <span class=\"c-#667085\">({")
                    .append(userDetail.getWorkNumber())
                    .append("})</span>以\n");
            sb.append("        </span>\n");
            sb.append(
                    "         <div class=\"arco-image arco-image-with-footer-inner m-r-8px cursor-pointer\" style=\"width: 28px; height: 28px; border-radius: 50%;\">\n");
            sb.append("         <img class=\"arco-image-img\" src=\"")
                    .append(mockUserDetail.getUserAvatar())
                    .append("\" style=\"width: 28px; height: 28px;\"><!--v-if--> <!--v-if-->\n");
            sb.append("       </div>\n");
            sb.append("        <span>\n");
            sb.append("          <span>{")
                    .append(mockUserDetail.getUserName())
                    .append("}</span>\n");
            sb.append("           <span class=\"c-#667085\">({")
                    .append(mockUserDetail.getWorkNumber())
                    .append("})</span>身份操作\n");
            sb.append("        </span>\n");
            sb.append("       </div>\n");
            sb.append("</span>");
            log.setUserInfo(sb.toString());
        }
        log.setMockUserName(mockUserDetail.getUserName());
        log.setLoginRoleName(roleConfigDetail.getRoleName());
        log.setMockRoleName(mockRoleConfigDetail.getRoleName());
        if (req.getId() == null || req.getId() == 0L) {
            jdecMonitorSystemLogMapper.insert(log);
        } else {
            jdecMonitorSystemLogMapper.updateById(log);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserDetailResp queryUserDetail(Long userId) {
        ApiResponse<UserDetailResp> userDetailResp =
                monitorLogApiClient.getUserDetailByUserId(userId);
        if (userDetailResp.getStatus() != 200 || userDetailResp.getData() == null) {
            throw new IllegalArgumentException("获取用户详情失败, userId: " + userId);
        }
        return userDetailResp.getData();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RoleConfigDetailResp queryRoleConfigDetail(Long roleId) {
        ApiResponse<RoleConfigDetailResp> roleConfigDetailResp =
                monitorLogApiClient.getRoleConfigDetail(roleId);
        if (roleConfigDetailResp.getStatus() != 200 || roleConfigDetailResp.getData() == null) {
            throw new IllegalArgumentException("获取角色配置详情失败, roleId: " + roleId);
        }
        return roleConfigDetailResp.getData();
    }

    private void validateOperationLogContent(String logContent) {
        JSONObject content;
        try {
            content = JSONUtil.parseObj(logContent);
        } catch (Exception e) {
            throw new IllegalArgumentException("操作日志内容必须是合法的JSON对象");
        }

        if (!StringUtils.hasText(content.getStr("module"))) {
            throw new IllegalArgumentException("操作日志内容中module不能为空");
        }
        if (!StringUtils.hasText(content.getStr("orderId"))) {
            throw new IllegalArgumentException("操作日志内容中orderId不能为空");
        }
        if (!StringUtils.hasText(content.getStr("hrefUrl"))) {
            throw new IllegalArgumentException("操作日志内容中hrefUrl不能为空");
        }

        boolean hasOperatorResult = StringUtils.hasText(content.getStr("operatorResult"));
        boolean hasFontColor = StringUtils.hasText(content.getStr("fontColor"));
        if (hasOperatorResult && !hasFontColor) {
            throw new IllegalArgumentException("操作结果不为空时，字体颜色不能为空");
        }
        if (!hasOperatorResult && hasFontColor) {
            throw new IllegalArgumentException("操作结果为空时，字体颜色必须为空");
        }
    }

    @Override
    public MonitorLogResp get(Long id, String logType) {
        JdecMonitorSystemLog log = jdecMonitorSystemLogMapper.selectById(id);
        if (log != null) {
            MonitorLogResp resp = BeanUtil.toBean(log, MonitorLogResp.class);
            if (LOG_TYPE_OPERATION.equals(log.getLogType())) {
                resp.setLogContent(formatOperationLogContent(log.getLogContent()));
            } else {
                resp.setLogContent(log.getLogContent());
            }
            resp.setLogRemark(log.getLogRemark());
            resp.setIp(log.getIp());
            return resp;
        }
        return null;
    }

    private String formatOperationLogContent(String logContent) {
        JSONObject content;
        try {
            content = JSONUtil.parseObj(logContent);
        } catch (Exception e) {
            return logContent;
        }
        /*
        {
            "module": "模块名称/功能名称/页面名称",
            "orderId": "单据编号/订单编号/业务编号/任务编号",
            "hrefUrl": "单据详情页/订单详情页/业务详情页/任务详情页",
            "orderVersion": "单据版本号/订单版本号/业务版本号/任务版本号",
            "controlName":"操作名称/单据名称",
            "operatorResult": "操作结果/执行结果/处理结果: 成功/失败",
            "fontColor": "字体颜色:默认为黑色"
        }
         */
        String module = content.getStr("module");
        String orderId = content.getStr("orderId");
        String hrefUrl = content.getStr("hrefUrl");
        String orderVersion = content.getStr("orderVersion");
        String controlName = content.getStr("controlName");
        String operatorResult = content.getStr("operatorResult");
        String fontColor = content.getStr("fontColor");

        StringBuilder sb = new StringBuilder();
        sb.append("<span class=\"arco-table-td-content\">\n");
        sb.append("    <div class=\"flex\">\n");
        sb.append("        <span>\n");
        sb.append("            <span>[")
                .append(module)
                .append("-")
                .append("<a style=\"color: #408bee\" href=\"")
                .append(hrefUrl)
                .append("\" target=\"_blank\">")
                .append(orderId)
                .append("</a>");
        if (StringUtils.hasText(orderVersion)) {
            sb.append("\n                <span style=\"font-weight: 700; color: #2e7fed\">")
                    .append(orderVersion)
                    .append("</span>");
        }
        sb.append("]\n");
        if (StringUtils.hasText(controlName)) {
            sb.append(controlName);
        }
        if (StringUtils.hasText(operatorResult)) {
            sb.append("                <span style=\"color: ")
                    .append(fontColor)
                    .append("\">")
                    .append(operatorResult)
                    .append("</span>\n");
        }
        sb.append("            </span>\n");
        sb.append("        </span>\n");
        sb.append("    </div>\n");
        sb.append("</span>");
        return sb.toString();
    }
}
