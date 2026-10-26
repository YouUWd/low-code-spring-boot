package com.jdec.platform.externalobject.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.externalobject.biz.entity.UserMenuHeader;
import com.jdec.platform.externalobject.biz.mapper.UserMenuHeaderMapper;
import com.jdec.platform.externalobject.biz.service.IUserMenuHeaderService;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import org.springframework.stereotype.Service;

/**
 * 用户自定义表头配置 服务实现类
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Service
@DataSource(DataSourceConstants.COMPANY)
public class UserMenuHeaderServiceImpl extends ServiceImpl<UserMenuHeaderMapper, UserMenuHeader>
        implements IUserMenuHeaderService {}
