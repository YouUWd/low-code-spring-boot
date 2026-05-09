package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import org.apache.ibatis.annotations.Mapper;

/** 模块基本信息 Mapper 接口 使用配置中心数据源 */
@Mapper
@DataSource(DataSourceConstants.CONFIG_CENTER)
public interface SysModuleMapper extends BaseMapper<SysModule> {}
