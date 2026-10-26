package com.jdec.platform.externalobject.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.externalobject.biz.entity.MonitorLog;
import com.jdec.platform.externalobject.biz.mapper.MonitorLogMapper;
import com.jdec.platform.externalobject.biz.service.IMonitorLogService;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import org.springframework.stereotype.Service;

/**
 * 监控日志 服务实现类
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Service
@DataSource(DataSourceConstants.COMPANY)
public class MonitorLogServiceImpl extends ServiceImpl<MonitorLogMapper, MonitorLog>
        implements IMonitorLogService {}
