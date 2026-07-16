package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.config.biz.entity.SysDataSnapshot;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import org.apache.ibatis.annotations.Mapper;

/** 数据快照 Mapper */
@Mapper
@DataSource(DataSourceConstants.CONFIG_CENTER)
public interface SysDataSnapshotMapper extends BaseMapper<SysDataSnapshot> {}
