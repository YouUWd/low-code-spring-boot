package com.jdec.platform.config.biz.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.config.api.SysScheduledTaskApi;
import com.jdec.platform.config.api.dto.request.CreateScheduledTaskReq;
import com.jdec.platform.config.api.dto.response.ScheduledTaskResp;
import com.jdec.platform.config.biz.entity.SysScheduledTask;
import com.jdec.platform.config.biz.mapper.SysScheduledTaskMapper;
import com.jdec.platform.shared.constant.VirtualUserConstants;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.security.JwtTokenProvider;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 定时 HTTP 任务 Service。
 *
 * <p>对外提供定时任务创建接口：任务落库后通过 {@link ThreadPoolTaskScheduler} 在指定时刻触发 HTTP
 * 调用。应用启动时会将数据库中状态为“新建”的任务重新加载到调度队列，保证重启不丢任务。
 *
 * <p>执行线程（调度线程）中通过 {@link #selfProvider} 获取自身代理调用 {@link #executeTask(Long)}， 确保
 * {@code @DataSource} 切面生效，数据库操作落在 {@code config_center} 数据源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_ENGINE)
public class SysScheduledTaskService extends ServiceImpl<SysScheduledTaskMapper, SysScheduledTask>
        implements SysScheduledTaskApi, ApplicationRunner {

    /** 任务状态: 新建 */
    public static final int STATUS_NEW = 0;

    /** 任务状态: 执行成功 */
    public static final int STATUS_SUCCESS = 1;

    /** 任务状态: 执行失败 */
    public static final int STATUS_FAILED = 2;

    /** HTTP 响应内容最大存储长度 */
    private static final int MAX_RESULT_LENGTH = 5000;

    /** 错误信息最大存储长度 */
    private static final int MAX_ERROR_LENGTH = 2000;

    private final ThreadPoolTaskScheduler scheduledTaskScheduler;
    private final RestTemplate restTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;

    /** 延迟获取自身代理，避免构造循环依赖；用于从调度线程调用时让数据源切面生效 */
    private final ObjectProvider<SysScheduledTaskService> selfProvider;

    // ==================== 创建与查询 ====================

    @Override
    @Transactional
    public Long createTask(String projectNo, Long subjectId, CreateScheduledTaskReq req) {
        if (req.getExecuteTime() == null) {
            throw new BusinessException("计划执行时间不能为空");
        }
        if (StrUtil.isBlank(req.getRequestUrl())) {
            throw new BusinessException("请求地址不能为空");
        }
        if (StrUtil.isBlank(req.getRequestMethod())) {
            throw new BusinessException("请求方式不能为空");
        }

        SysScheduledTask task =
                SysScheduledTask.builder()
                        .projectNo(StrUtil.isNotBlank(projectNo) ? projectNo : "")
                        .subjectId(subjectId)
                        .taskName(req.getTaskName())
                        .requestUrl(req.getRequestUrl())
                        .requestMethod(req.getRequestMethod().toUpperCase())
                        .requestHeaders(req.getRequestHeaders())
                        .requestBody(req.getRequestBody())
                        .requestParams(req.getRequestParams())
                        .executeTime(req.getExecuteTime())
                        .taskStatus(STATUS_NEW)
                        .executeCount(0)
                        .build();
        this.save(task);

        // 事务提交成功后再调度，避免事务回滚后任务已注册但未落库
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        scheduleTask(task);
                    }
                });
        log.info(
                "创建定时任务: id={}, taskName={}, executeTime={}",
                task.getId(),
                task.getTaskName(),
                task.getExecuteTime());
        return task.getId();
    }

    @Override
    public ScheduledTaskResp getTaskById(Long id) {
        SysScheduledTask task = this.getById(id);
        if (task == null) {
            throw new BusinessException("任务不存在: " + id);
        }
        return toResp(task);
    }

    @Override
    public List<ScheduledTaskResp> listTasksByStatus(Integer status) {
        return this.list(
                        new LambdaQueryWrapper<SysScheduledTask>()
                                .eq(status != null, SysScheduledTask::getTaskStatus, status)
                                .orderByDesc(SysScheduledTask::getExecuteTime))
                .stream()
                .map(this::toResp)
                .toList();
    }

    @Override
    public boolean executeNow(Long id) {
        SysScheduledTask task = this.getById(id);
        if (task == null) {
            throw new BusinessException("任务不存在: " + id);
        }
        selfProvider.getObject().executeTask(id);
        return true;
    }

    // ==================== 调度与执行 ====================

    /** 注册任务到调度器，到达计划执行时间后执行一次 HTTP 调用 */
    public void scheduleTask(SysScheduledTask task) {
        scheduledTaskScheduler.schedule(
                () -> selfProvider.getObject().executeTask(task.getId()),
                Date.from(task.getExecuteTime().atZone(ZoneId.systemDefault()).toInstant()));
        log.info("定时任务已调度: id={}, executeTime={}", task.getId(), task.getExecuteTime());
    }

    /**
     * 执行 HTTP 任务并更新执行状态。
     *
     * <p>注意：必须经由代理调用（见 {@link #selfProvider}），使 {@code @DataSource} 切面在当前调度线程生效。
     *
     * @param taskId 任务 ID
     */
    public void executeTask(Long taskId) {
        SysScheduledTask task = this.getById(taskId);
        if (task == null) {
            log.warn("定时任务不存在，跳过执行: taskId={}", taskId);
            return;
        }
        LocalDateTime start = LocalDateTime.now();
        int count = (task.getExecuteCount() == null ? 0 : task.getExecuteCount()) + 1;
        try {
            String result = doHttpRequest(task);
            task.setTaskStatus(STATUS_SUCCESS);
            task.setExecuteResult(truncate(result, MAX_RESULT_LENGTH));
            task.setErrorMsg(null);
            log.info(
                    "定时任务执行成功: taskId={}, taskName={}, count={}",
                    taskId,
                    task.getTaskName(),
                    count);
        } catch (Exception e) {
            task.setTaskStatus(STATUS_FAILED);
            task.setErrorMsg(truncate(e.getMessage(), MAX_ERROR_LENGTH));
            log.error(
                    "定时任务执行失败: taskId={}, taskName={}, count={}",
                    taskId,
                    task.getTaskName(),
                    count,
                    e);
        }
        task.setExecuteCount(count);
        task.setExecuteStartTime(start);
        task.setExecuteEndTime(LocalDateTime.now());
        this.updateById(task);
    }

    // ==================== 重启恢复 ====================

    /** 应用启动完成后，将状态为“新建”的任务重新加载到调度队列 */
    @Override
    public void run(ApplicationArguments args) {
        reloadPendingTasks();
    }

    /** 加载所有未执行（新建）的定时任务并重新调度 */
    public void reloadPendingTasks() {
        try {
            List<SysScheduledTask> pending =
                    this.list(
                            new LambdaQueryWrapper<SysScheduledTask>()
                                    .eq(SysScheduledTask::getTaskStatus, STATUS_NEW));
            for (SysScheduledTask task : pending) {
                scheduleTask(task);
            }
            log.info("项目启动，恢复未执行定时任务 {} 个", pending.size());
        } catch (Exception e) {
            log.error("项目启动恢复定时任务失败", e);
        }
    }

    // ==================== 私有方法 ====================

    /** 执行 HTTP 请求，返回响应体 */
    private String doHttpRequest(SysScheduledTask task) {
        String url = buildUrl(task.getRequestUrl(), parseMap(task.getRequestParams()));
        HttpMethod method = HttpMethod.valueOf(task.getRequestMethod().toUpperCase());
        HttpHeaders headers = buildHeaders(task.getRequestHeaders());

        // 生成虚拟用户的 JWT token（1天过期）
        String token =
                jwtTokenProvider.generateToken(
                        VirtualUserConstants.SCHEDULED_TASK_USER_ID,
                        VirtualUserConstants.SCHEDULED_TASK_USERNAME,
                        VirtualUserConstants.SCHEDULED_TASK_PHONE,
                        null,
                        VirtualUserConstants.TOKEN_EXPIRATION_ONE_DAY);

        // 将 token 存入 Redis，key 为 token:手机号
        String redisKey = "token:" + VirtualUserConstants.SCHEDULED_TASK_PHONE;
        stringRedisTemplate.opsForValue().set(redisKey, token, 1, TimeUnit.DAYS);

        headers.set("Authorization", "Bearer " + token);

        HttpEntity<String> entity =
                (method == HttpMethod.GET || method == HttpMethod.DELETE)
                        ? new HttpEntity<>(headers)
                        : new HttpEntity<>(task.getRequestBody(), headers);
        ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
        return response.getBody();
    }

    /** 将 query string 参数拼接进 URL */
    private String buildUrl(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        params.forEach(builder::queryParam);
        return builder.build().encode().toUriString();
    }

    /** 构建请求头 */
    private HttpHeaders buildHeaders(String headersJson) {
        HttpHeaders headers = new HttpHeaders();
        Map<String, String> map = parseMap(headersJson);
        if (map != null) {
            map.forEach(headers::set);
        }
        return headers;
    }

    /** 解析 JSON 字符串为 Map，解析失败返回空 Map */
    private Map<String, String> parseMap(String json) {
        if (StrUtil.isBlank(json)) {
            return new HashMap<>();
        }
        try {
            return JSON.parseObject(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("JSON 解析失败，按空处理: {}", json);
            return new HashMap<>();
        }
    }

    /** 截断超长文本，避免撑爆存储 */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    /** 实体转响应 DTO */
    private ScheduledTaskResp toResp(SysScheduledTask task) {
        ScheduledTaskResp resp = new ScheduledTaskResp();
        resp.setId(task.getId());
        resp.setProjectNo(task.getProjectNo());
        resp.setSubjectId(task.getSubjectId());
        resp.setTaskName(task.getTaskName());
        resp.setRequestUrl(task.getRequestUrl());
        resp.setRequestMethod(task.getRequestMethod());
        resp.setRequestHeaders(task.getRequestHeaders());
        resp.setRequestBody(task.getRequestBody());
        resp.setRequestParams(task.getRequestParams());
        resp.setExecuteTime(task.getExecuteTime());
        resp.setTaskStatus(task.getTaskStatus());
        resp.setExecuteCount(task.getExecuteCount());
        resp.setExecuteResult(task.getExecuteResult());
        resp.setErrorMsg(task.getErrorMsg());
        resp.setExecuteStartTime(task.getExecuteStartTime());
        resp.setExecuteEndTime(task.getExecuteEndTime());
        resp.setCreatedBy(task.getCreatedBy());
        resp.setCreatedDate(task.getCreatedDate());
        resp.setCreatedName(task.getCreatedName());
        resp.setUpdatedBy(task.getUpdatedBy());
        resp.setUpdatedDate(task.getUpdatedDate());
        resp.setUpdatedName(task.getUpdatedName());
        return resp;
    }
}
