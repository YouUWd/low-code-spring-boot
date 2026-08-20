package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.config.biz.entity.SysScheduledTask;
import org.apache.ibatis.annotations.Mapper;

/** 定时任务 Mapper 接口 */
@Mapper
public interface SysScheduledTaskMapper extends BaseMapper<SysScheduledTask> {}
