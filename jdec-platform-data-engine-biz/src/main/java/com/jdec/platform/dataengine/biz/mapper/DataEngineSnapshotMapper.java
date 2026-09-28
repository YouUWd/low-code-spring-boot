package com.jdec.platform.dataengine.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.dataengine.biz.entity.DataEngineSnapshot;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据引擎-业务聚合多版本快照表 Mapper 接口
 *
 * @author
 * @since 2026-08-26
 */
@Mapper
public interface DataEngineSnapshotMapper extends BaseMapper<DataEngineSnapshot> {}
