package com.jdec.platform.externalobject.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.externalobject.biz.entity.Venue;
import com.jdec.platform.externalobject.biz.mapper.VenueMapper;
import com.jdec.platform.externalobject.biz.service.IVenueService;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import org.springframework.stereotype.Service;

/**
 * 场地表 服务实现类
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Service
@DataSource(DataSourceConstants.COMPANY)
public class VenueServiceImpl extends ServiceImpl<VenueMapper, Venue> implements IVenueService {}
