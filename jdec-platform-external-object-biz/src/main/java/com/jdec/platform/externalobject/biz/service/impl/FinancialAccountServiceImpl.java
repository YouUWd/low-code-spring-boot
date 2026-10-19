package com.jdec.platform.externalobject.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.externalobject.biz.entity.FinancialAccount;
import com.jdec.platform.externalobject.biz.mapper.FinancialAccountMapper;
import com.jdec.platform.externalobject.biz.service.IFinancialAccountService;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import org.springframework.stereotype.Service;

/**
 * 外部对象银行卡结算账户表（Tab 4-1） 服务实现类
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Service
@DataSource(DataSourceConstants.COMPANY)
public class FinancialAccountServiceImpl
        extends ServiceImpl<FinancialAccountMapper, FinancialAccount>
        implements IFinancialAccountService {}
