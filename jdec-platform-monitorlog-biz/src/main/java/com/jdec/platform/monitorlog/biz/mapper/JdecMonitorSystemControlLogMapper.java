package com.jdec.platform.monitorlog.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.monitorlog.biz.entity.JdecMonitorSystemControlLog;
import org.apache.ibatis.annotations.Mapper;

/** 系统控制日志Mapper */
@Mapper
public interface JdecMonitorSystemControlLogMapper
        extends BaseMapper<JdecMonitorSystemControlLog> {}
