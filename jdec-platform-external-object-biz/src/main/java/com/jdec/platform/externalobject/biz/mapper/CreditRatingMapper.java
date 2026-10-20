package com.jdec.platform.externalobject.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.externalobject.biz.entity.CreditRating;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外部对象信用等级表（Tab 5 信用等级） Mapper 接口
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Mapper
public interface CreditRatingMapper extends BaseMapper<CreditRating> {}
