package com.jdec.platform.externalobject.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.externalobject.biz.entity.DigitalAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外部对象电子结算账户表（Tab 4-2） Mapper 接口
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Mapper
public interface DigitalAccountMapper extends BaseMapper<DigitalAccount> {}
