package com.jdec.platform.monitorlog.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.monitorlog.biz.entity.JdecMonitorOperationLog;
import org.apache.ibatis.annotations.Mapper;

/** 监控日志 Mapper */
@Mapper
public interface JdecMonitorOperationLogMapper extends BaseMapper<JdecMonitorOperationLog> {}
