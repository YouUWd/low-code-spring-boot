package com.jdec.platform.externalobject.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.externalobject.biz.entity.MemberContact;
import com.jdec.platform.externalobject.biz.mapper.MemberContactMapper;
import com.jdec.platform.externalobject.biz.service.IMemberContactService;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import org.springframework.stereotype.Service;

/**
 * 人员联系方式表（兼具个人通讯录与公司对外联络窗口职责） 服务实现类
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Service
@DataSource(DataSourceConstants.COMPANY)
public class MemberContactServiceImpl extends ServiceImpl<MemberContactMapper, MemberContact>
        implements IMemberContactService {}
