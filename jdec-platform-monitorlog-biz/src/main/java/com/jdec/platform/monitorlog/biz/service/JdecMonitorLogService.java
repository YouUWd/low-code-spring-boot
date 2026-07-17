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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    // 监控日志Mapper
    private final JdecMonitorSystemLogMapper jdecMonitorSystemLogMapper;
    // 监控日志API客户端
    private final MonitorLogApiClient monitorLogApiClient;
    // 日期格式化器，用于格式化日期
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String LOG_TYPE_MESSAGE = "message-log";
    private static final String LOG_TYPE_OPERATION = "operation-log";
    private static final String LOG_TYPE_SYSTEM = "system-log";

    private static final String USER_INFO_TEMPLATE =
            "<span class=\"arco-table-td-content\">"
                    + "<div class=\"flex\">"
                    + "<span><div class=\"flex flex-items-center\"></div></span>"
                    + "<div class=\"arco-image arco-image-with-footer-inner m-r-8px cursor-pointer\" style=\"width: 28px; height: 28px; border-radius: 50%;\">"
                    + "<img class=\"arco-image-img\" src=\"{avatar}\" style=\"width: 28px; height: 28px;\"></div>"
                    + "<span><span>{userName}</span>"
                    + "<span class=\"c-#667085\">({workNumber})</span></span>"
                    + "</div></span>";
    // 模拟用户信息模板，用于测试
    private static final String MOCK_USER_INFO_TEMPLATE =
            "<span class=\"arco-table-td-content\">"
                    + "<div class=\"flex\">"
                    + "<span><div class=\"flex flex-items-center\"></div></span>"
                    + "<div class=\"arco-image arco-image-with-footer-inner m-r-8px cursor-pointer\" style=\"width: 28px; height: 28px; border-radius: 50%;\">"
                    + "<img class=\"arco-image-img\" src=\"{userAvatar}\" style=\"width: 28px; height: 28px;\"></div>"
                    + "<span><span>{userName}</span>"
                    + "<span class=\"c-#667085\">({userWorkNumber})</span>以</span>"
                    + "<div class=\"arco-image arco-image-with-footer-inner m-r-8px cursor-pointer\" style=\"width: 28px; height: 28px; border-radius: 50%;\">"
                    + "<img class=\"arco-image-img\" src=\"{mockAvatar}\" style=\"width: 28px; height: 28px;\"></div>"
                    + "<span><span>{mockUserName}</span>"
                    + "<span class=\"c-#667085\">({mockWorkNumber})</span>身份操作</span>"
                    + "</div></span>";
    // 缓存配置，最大缓存大小
    private static final int CACHE_MAX_SIZE = 50;

    // 缓存条目包装类，包含数据和最后访问时间
    private static class CacheEntry<T> {
        private final T data;
        private volatile long lastAccessTime;

        CacheEntry(T data) {
            this.data = data;
            this.lastAccessTime = System.currentTimeMillis();
        }

        void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }

    // 两个独立的缓存Map（线程安全）
    private final Map<Long, CacheEntry<UserDetailResp>> userDetailCache = new ConcurrentHashMap<>();
    private final Map<Long, CacheEntry<RoleConfigDetailResp>> roleConfigDetailCache =
            new ConcurrentHashMap<>();

    @Override
    public List<MonitorLogPageListResp> queryPageList(MonitorLogPageListReq request) {
        LocalDate startDate = LocalDate.parse(request.getStartTime(), DATE_FORMATTER);
        LocalDate endDate = LocalDate.parse(request.getEndTime(), DATE_FORMATTER);
        LocalDateTime startDateTime = LocalDateTime.of(startDate, LocalTime.MIN);
        LocalDateTime endDateTime = LocalDateTime.of(endDate, LocalTime.MAX);
        // 分页查询监控日志
        Page<JdecMonitorSystemLog> page = new Page<>(request.getPage(), request.getPageSize());
        // 构建查询条件
        LambdaQueryWrapper<JdecMonitorSystemLog> wrapper =
                buildQueryWrapper(request, startDateTime, endDateTime);
        // 执行分页查询
        IPage<JdecMonitorSystemLog> result = jdecMonitorSystemLogMapper.selectPage(page, wrapper);

        return result.getRecords().stream().map(this::convertToResp).collect(Collectors.toList());
    }

    private LambdaQueryWrapper<JdecMonitorSystemLog> buildQueryWrapper(
            MonitorLogPageListReq request, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        // 构建查询条件
        LambdaQueryWrapper<JdecMonitorSystemLog> wrapper =
                new LambdaQueryWrapper<JdecMonitorSystemLog>()
                        .like(
                                StringUtils.hasText(request.getKeyWord()),
                                JdecMonitorSystemLog::getLogContent,
                                request.getKeyWord())
                        .between(JdecMonitorSystemLog::getLogDate, startDateTime, endDateTime)
                        .orderByDesc(JdecMonitorSystemLog::getLogDate);
        // 如果指定了日志类型，添加等于条件
        if (StringUtils.hasText(request.getLogType())) {
            wrapper.eq(JdecMonitorSystemLog::getLogType, request.getLogType());
        }
        return wrapper;
    }

    private MonitorLogPageListResp convertToResp(JdecMonitorSystemLog log) {
        MonitorLogPageListResp resp = BeanUtil.toBean(log, MonitorLogPageListResp.class);
        // 格式化日志内容
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
        // 校验操作日志内容
        if (LOG_TYPE_OPERATION.equals(req.getLogType())) {
            validateOperationLogContent(req.getLogContent());
        }
        // 查询用户详情
        UserDetailResp userDetail = new UserDetailResp();
        if (req.getLoginUserId() != null && req.getLoginUserId() != 0L) {
            userDetail = queryUserDetail(req.getLoginUserId());
        }
        // 查询角色详情
        RoleConfigDetailResp roleConfigDetail = new RoleConfigDetailResp();
        if (req.getLoginRoleId() != null && req.getLoginRoleId() != 0L) {
            roleConfigDetail = queryRoleConfigDetail(req.getLoginRoleId());
        }
        // 查询模拟用户详情
        UserDetailResp mockUserDetail = new UserDetailResp();
        if (req.getMockUserId() != null && req.getMockUserId() != 0L) {
            mockUserDetail = queryUserDetail(req.getMockUserId());
        }
        // 查询模拟角色详情
        RoleConfigDetailResp mockRoleConfigDetail = new RoleConfigDetailResp();
        if (req.getMockRoleId() != null && req.getMockRoleId() != 0L) {
            mockRoleConfigDetail = queryRoleConfigDetail(req.getMockRoleId());
        }
        // 构建日志实体
        JdecMonitorSystemLog log = BeanUtil.toBean(req, JdecMonitorSystemLog.class);
        // 构建用户信息
        if (req.getMockFlag() == 0) {
            String userInfo =
                    USER_INFO_TEMPLATE
                            .replace("{avatar}", userDetail.getUserAvatar())
                            .replace("{userName}", userDetail.getUserName())
                            .replace("{workNumber}", userDetail.getWorkNumber());
            log.setUserInfo(userInfo);
        } else {
            String mockUserInfo =
                    MOCK_USER_INFO_TEMPLATE
                            .replace("{userAvatar}", userDetail.getUserAvatar())
                            .replace("{userName}", userDetail.getUserName())
                            .replace("{userWorkNumber}", userDetail.getWorkNumber())
                            .replace("{mockAvatar}", mockUserDetail.getUserAvatar())
                            .replace("{mockUserName}", mockUserDetail.getUserName())
                            .replace("{mockWorkNumber}", mockUserDetail.getWorkNumber());
            log.setUserInfo(mockUserInfo);
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

    public UserDetailResp queryUserDetail(Long userId) {
        // 1️ 优先从缓存读取
        CacheEntry<UserDetailResp> cacheEntry = userDetailCache.get(userId);
        if (cacheEntry != null) {
            cacheEntry.updateAccessTime();
            return cacheEntry.data;
        }
        // 2️ 缓存未命中，调用API获取
        UserDetailResp userDetail = fetchUserDetailFromApi(userId);
        // 3️ 缓存满时，淘汰最久未使用的记录
        if (userDetailCache.size() >= CACHE_MAX_SIZE) {
            evictLRUEntry(userDetailCache);
        }
        // 4️ 加入缓存
        userDetailCache.put(userId, new CacheEntry<>(userDetail));

        return userDetail;
    }

    private UserDetailResp fetchUserDetailFromApi(Long userId) {
        // 调用API获取用户详情
        ApiResponse<UserDetailResp> userDetailResp =
                monitorLogApiClient.getUserDetailByUserId(userId);
        if (userDetailResp.getStatus() != 200 || userDetailResp.getData() == null) {
            throw new IllegalArgumentException("获取用户详情失败, userId: " + userId);
        }
        return userDetailResp.getData();
    }

    public RoleConfigDetailResp queryRoleConfigDetail(Long roleId) {
        // 1️ 优先从缓存读取
        CacheEntry<RoleConfigDetailResp> cacheEntry = roleConfigDetailCache.get(roleId);
        if (cacheEntry != null) {
            // 更新访问时间
            cacheEntry.updateAccessTime();
            return cacheEntry.data;
        }
        // 2️ 缓存未命中，调用API获取
        RoleConfigDetailResp roleConfigDetail = fetchRoleConfigDetailFromApi(roleId);
        // 3️ 缓存满时，淘汰最久未使用的记录
        if (roleConfigDetailCache.size() >= CACHE_MAX_SIZE) {
            evictLRUEntry(roleConfigDetailCache);
        }
        // 4️ 加入缓存
        roleConfigDetailCache.put(roleId, new CacheEntry<>(roleConfigDetail));

        return roleConfigDetail;
    }

    private RoleConfigDetailResp fetchRoleConfigDetailFromApi(Long roleId) {
        ApiResponse<RoleConfigDetailResp> roleConfigDetailResp =
                monitorLogApiClient.getRoleConfigDetail(roleId);
        if (roleConfigDetailResp.getStatus() != 200 || roleConfigDetailResp.getData() == null) {
            throw new IllegalArgumentException("获取角色配置详情失败, roleId: " + roleId);
        }
        return roleConfigDetailResp.getData();
    }

    private <T> void evictLRUEntry(Map<Long, CacheEntry<T>> cache) {
        Long lruKey = null;
        long oldestTime = Long.MAX_VALUE;
        // 遍历找到最后访问时间最早的记录
        for (Map.Entry<Long, CacheEntry<T>> entry : cache.entrySet()) {
            if (entry.getValue().lastAccessTime < oldestTime) {
                oldestTime = entry.getValue().lastAccessTime;
                lruKey = entry.getKey();
            }
        }
        // 移除该记录
        if (lruKey != null) {
            cache.remove(lruKey);
        }
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
            // 从缓存中获取用户详情
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

        String orderVersionHtml =
                StringUtils.hasText(orderVersion)
                        ? "<span style=\"font-weight: 700; color: #2e7fed\">"
                                + orderVersion
                                + "</span>"
                        : "";
        String controlNameHtml = StringUtils.hasText(controlName) ? controlName : "";
        String operatorResultHtml =
                StringUtils.hasText(operatorResult)
                        ? "<span style=\"color: " + fontColor + "\">" + operatorResult + "</span>"
                        : "";
        return "<span class=\"arco-table-td-content\">"
                + "<div class=\"flex\"><span><span>["
                + module
                + "-<a style=\"color: #408bee\" href=\""
                + hrefUrl
                + "\" target=\"_blank\">"
                + orderId
                + "</a>"
                + orderVersionHtml
                + "]"
                + controlNameHtml
                + operatorResultHtml
                + "</span></div></span>";
    }
}
