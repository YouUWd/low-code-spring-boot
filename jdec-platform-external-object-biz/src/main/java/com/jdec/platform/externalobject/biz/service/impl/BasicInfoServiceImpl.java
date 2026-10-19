package com.jdec.platform.externalobject.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.externalobject.biz.entity.BasicInfo;
import com.jdec.platform.externalobject.biz.mapper.BasicInfoMapper;
import com.jdec.platform.externalobject.biz.service.IBasicInfoService;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import org.springframework.stereotype.Service;

/**
 * 外部对象工商与法定资质表（Tab 1 基本信息） 服务实现类
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Service
@DataSource(DataSourceConstants.COMPANY)
public class BasicInfoServiceImpl extends ServiceImpl<BasicInfoMapper, BasicInfo>
        implements IBasicInfoService {}
