package com.jdec.platform.dataengine.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.dataengine.biz.entity.SysModule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 低代码业务模块定义表 Mapper 接口
 *
 * @author
 * @since 2026-09-14
 */
@Mapper
public interface MySysModuleMapper extends BaseMapper<SysModule> {}
