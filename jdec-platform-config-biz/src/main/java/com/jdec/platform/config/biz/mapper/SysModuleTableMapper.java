package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.config.biz.entity.SysModuleTable;
import org.apache.ibatis.annotations.Mapper;

/** 模块表 Mapper 接口 使用配置中心数据源 */
@Mapper
public interface SysModuleTableMapper extends BaseMapper<SysModuleTable> {}
