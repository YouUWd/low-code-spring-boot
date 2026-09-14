package com.jdec.platform.dataengine.biz.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.dataengine.biz.config.entity.DataSysModuleField;
import org.apache.ibatis.annotations.Mapper;

/** 模块字段元数据 Mapper */
@Mapper
public interface DataSysModuleFieldMapper extends BaseMapper<DataSysModuleField> {}
