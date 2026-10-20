package com.jdec.platform.externalobject.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.externalobject.biz.entity.CreditRating;
import com.jdec.platform.externalobject.biz.mapper.CreditRatingMapper;
import com.jdec.platform.externalobject.biz.service.ICreditRatingService;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import org.springframework.stereotype.Service;

/**
 * 外部对象信用等级表（Tab 5 信用等级） 服务实现类
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Service
@DataSource(DataSourceConstants.COMPANY)
public class CreditRatingServiceImpl extends ServiceImpl<CreditRatingMapper, CreditRating>
        implements ICreditRatingService {}
