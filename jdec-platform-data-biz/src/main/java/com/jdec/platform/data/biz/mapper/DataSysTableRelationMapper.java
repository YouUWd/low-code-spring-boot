package com.jdec.platform.data.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.data.biz.entity.DataSysTableRelation;
import org.apache.ibatis.annotations.Mapper;

/** 物理表关联关系 Mapper */
@Mapper
public interface DataSysTableRelationMapper extends BaseMapper<DataSysTableRelation> {}
