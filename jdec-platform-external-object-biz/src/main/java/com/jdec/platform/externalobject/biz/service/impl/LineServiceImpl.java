package com.jdec.platform.externalobject.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.externalobject.biz.entity.Line;
import com.jdec.platform.externalobject.biz.mapper.LineMapper;
import com.jdec.platform.externalobject.biz.service.ILineService;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import org.springframework.stereotype.Service;

/**
 * 产线表 服务实现类
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Service
@DataSource(DataSourceConstants.COMPANY)
public class LineServiceImpl extends ServiceImpl<LineMapper, Line> implements ILineService {}
