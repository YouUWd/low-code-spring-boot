package com.jdec.platform.dataengine.biz.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.dataengine.biz.config.entity.DataSysModule;
import org.apache.ibatis.annotations.Mapper;

/** 模块元数据 Mapper */
@Mapper
public interface DataSysModuleMapper extends BaseMapper<DataSysModule> {}
