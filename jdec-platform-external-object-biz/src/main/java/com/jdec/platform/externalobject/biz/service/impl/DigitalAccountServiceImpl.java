package com.jdec.platform.externalobject.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.externalobject.biz.entity.DigitalAccount;
import com.jdec.platform.externalobject.biz.mapper.DigitalAccountMapper;
import com.jdec.platform.externalobject.biz.service.IDigitalAccountService;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import org.springframework.stereotype.Service;

/**
 * 外部对象电子结算账户表（Tab 4-2） 服务实现类
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Service
@DataSource(DataSourceConstants.COMPANY)
public class DigitalAccountServiceImpl extends ServiceImpl<DigitalAccountMapper, DigitalAccount>
        implements IDigitalAccountService {}
