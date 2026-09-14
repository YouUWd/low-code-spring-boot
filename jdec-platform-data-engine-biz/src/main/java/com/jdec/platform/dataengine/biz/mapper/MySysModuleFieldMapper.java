package com.jdec.platform.dataengine.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.dataengine.biz.entity.SysModuleField;
import org.apache.ibatis.annotations.Mapper;

/**
 * 低代码模块物理字段字典表 Mapper 接口
 *
 * @author
 * @since 2026-09-14
 */
@Mapper
public interface MySysModuleFieldMapper extends BaseMapper<SysModuleField> {}
