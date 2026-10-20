package com.jdec.platform.externalobject.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.externalobject.biz.entity.FinancialAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外部对象银行卡结算账户表（Tab 4-1） Mapper 接口
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Mapper
public interface FinancialAccountMapper extends BaseMapper<FinancialAccount> {}
