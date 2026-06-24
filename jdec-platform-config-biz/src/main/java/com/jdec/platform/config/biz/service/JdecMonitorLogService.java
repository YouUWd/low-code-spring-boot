package com.jdec.platform.config.biz.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.api.dto.request.MonitorLogQueryReq;
import com.jdec.platform.config.api.dto.response.MonitorLogResp;
import com.jdec.platform.config.biz.entity.JdecMonitorLog;
import com.jdec.platform.config.biz.mapper.JdecMonitorLogMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 监控日志 Service */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class JdecMonitorLogService {

    private final JdecMonitorLogMapper jdecMonitorLogMapper;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 查询监控日志列表
     *
     * @param request 查询请求
     * @return 日志列表
     */
    public List<MonitorLogResp> queryList(MonitorLogQueryReq request) {
        // 解析时间
        LocalDate startDate = LocalDate.parse(request.getStartTime(), DATE_FORMATTER);
        LocalDate endDate = LocalDate.parse(request.getEndTime(), DATE_FORMATTER);
        LocalDateTime startDateTime = LocalDateTime.of(startDate, LocalTime.MIN);
        LocalDateTime endDateTime = LocalDateTime.of(endDate, LocalTime.MAX);

        // 构建查询条件
        LambdaQueryWrapper<JdecMonitorLog> wrapper =
                new LambdaQueryWrapper<JdecMonitorLog>()
                        .eq(
                                StrUtil.isNotBlank(request.getLogType()),
                                JdecMonitorLog::getLogType,
                                request.getLogType())
                        .like(
                                StrUtil.isNotBlank(request.getKeyWord()),
                                JdecMonitorLog::getContent,
                                request.getKeyWord())
                        .between(JdecMonitorLog::getLogDate, startDateTime, endDateTime)
                        .orderByDesc(JdecMonitorLog::getLogDate)
                        .last("LIMIT " + request.getSize());

        // 查询并转换
        List<JdecMonitorLog> logs = jdecMonitorLogMapper.selectList(wrapper);

        return logs.stream()
                .map(
                        log -> {
                            MonitorLogResp resp = BeanUtil.toBean(log, MonitorLogResp.class);
                            return resp;
                        })
                .collect(Collectors.toList());
    }
}
