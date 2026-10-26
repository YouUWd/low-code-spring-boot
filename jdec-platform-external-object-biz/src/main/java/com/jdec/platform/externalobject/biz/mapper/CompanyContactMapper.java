package com.jdec.platform.externalobject.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.externalobject.biz.entity.CompanyContact;
import org.apache.ibatis.annotations.Mapper;

/**
 * 公司联系方式表（对外联络窗口与公开渠道明细） Mapper 接口
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Mapper
public interface CompanyContactMapper extends BaseMapper<CompanyContact> {}
