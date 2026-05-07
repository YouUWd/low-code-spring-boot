package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.config.biz.entity.SysModuleSimpleField;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模块简单字段 Mapper 接口
 * 使用配置中心数据源
 */
@Mapper
@DataSource(DataSourceConstants.CONFIG_CENTER)
public interface SysModuleSimpleFieldMapper extends BaseMapper<SysModuleSimpleField> {
}
