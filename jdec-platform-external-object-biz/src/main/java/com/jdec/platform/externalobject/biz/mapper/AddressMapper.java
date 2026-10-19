package com.jdec.platform.externalobject.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.externalobject.biz.entity.Address;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外部对象地址信息表（Tab 3 地址部分） Mapper 接口
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Mapper
public interface AddressMapper extends BaseMapper<Address> {}
