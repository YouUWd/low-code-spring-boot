package com.jdec.platform.data.biz.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.data.biz.config.entity.DataSysTableRelation;
import org.apache.ibatis.annotations.Mapper;

/** 表关联拓扑元数据 Mapper */
@Mapper
public interface DataSysTableRelationMapper extends BaseMapper<DataSysTableRelation> {}
