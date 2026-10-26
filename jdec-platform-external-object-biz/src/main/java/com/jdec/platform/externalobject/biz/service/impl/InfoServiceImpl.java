package com.jdec.platform.externalobject.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.externalobject.biz.entity.Info;
import com.jdec.platform.externalobject.biz.mapper.InfoMapper;
import com.jdec.platform.externalobject.biz.service.IInfoService;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import org.springframework.stereotype.Service;

/**
 * 外部对象主档表（顶部公共头核心信息） 服务实现类
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Service
@DataSource(DataSourceConstants.COMPANY)
public class InfoServiceImpl extends ServiceImpl<InfoMapper, Info> implements IInfoService {}
