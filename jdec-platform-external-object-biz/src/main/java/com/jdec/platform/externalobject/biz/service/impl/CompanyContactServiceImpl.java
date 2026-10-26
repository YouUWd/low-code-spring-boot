package com.jdec.platform.externalobject.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.externalobject.biz.entity.CompanyContact;
import com.jdec.platform.externalobject.biz.mapper.CompanyContactMapper;
import com.jdec.platform.externalobject.biz.service.ICompanyContactService;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import org.springframework.stereotype.Service;

/**
 * 公司联系方式表（对外联络窗口与公开渠道明细） 服务实现类
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Service
@DataSource(DataSourceConstants.COMPANY)
public class CompanyContactServiceImpl extends ServiceImpl<CompanyContactMapper, CompanyContact>
        implements ICompanyContactService {}
