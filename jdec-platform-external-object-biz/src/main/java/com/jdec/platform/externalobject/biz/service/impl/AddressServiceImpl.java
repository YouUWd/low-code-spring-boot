package com.jdec.platform.externalobject.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.externalobject.biz.entity.Address;
import com.jdec.platform.externalobject.biz.mapper.AddressMapper;
import com.jdec.platform.externalobject.biz.service.IAddressService;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import org.springframework.stereotype.Service;

/**
 * 外部对象地址信息表（Tab 3 地址部分） 服务实现类
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Service
@DataSource(DataSourceConstants.COMPANY)
public class AddressServiceImpl extends ServiceImpl<AddressMapper, Address>
        implements IAddressService {}
