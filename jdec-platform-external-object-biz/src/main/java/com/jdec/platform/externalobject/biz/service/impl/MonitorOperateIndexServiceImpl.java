package com.jdec.platform.externalobject.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.externalobject.biz.entity.MonitorOperateIndex;
import com.jdec.platform.externalobject.biz.mapper.MonitorOperateIndexMapper;
import com.jdec.platform.externalobject.biz.service.IMonitorOperateIndexService;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import org.springframework.stereotype.Service;

/**
 * 操作日志索引表 服务实现类
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Service
@DataSource(DataSourceConstants.COMPANY)
public class MonitorOperateIndexServiceImpl
        extends ServiceImpl<MonitorOperateIndexMapper, MonitorOperateIndex>
        implements IMonitorOperateIndexService {}
