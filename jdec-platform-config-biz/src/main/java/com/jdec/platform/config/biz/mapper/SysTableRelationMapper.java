package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.config.biz.entity.SysTableRelation;
import org.apache.ibatis.annotations.Mapper;

/** 全局物理表关联关系 Mapper */
@Mapper
public interface SysTableRelationMapper extends BaseMapper<SysTableRelation> {}
