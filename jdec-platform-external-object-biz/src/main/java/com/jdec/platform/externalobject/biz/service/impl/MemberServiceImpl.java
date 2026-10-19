package com.jdec.platform.externalobject.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.externalobject.biz.entity.Member;
import com.jdec.platform.externalobject.biz.mapper.MemberMapper;
import com.jdec.platform.externalobject.biz.service.IMemberService;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import org.springframework.stereotype.Service;

/**
 * 外部对象组织与人员树形表（Tab 2 人员构成） 服务实现类
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Service
@DataSource(DataSourceConstants.COMPANY)
public class MemberServiceImpl extends ServiceImpl<MemberMapper, Member>
        implements IMemberService {}
