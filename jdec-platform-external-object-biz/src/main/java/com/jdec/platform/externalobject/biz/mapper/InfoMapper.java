package com.jdec.platform.externalobject.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.externalobject.biz.entity.Info;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外部对象主档表（顶部公共头核心信息） Mapper 接口
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Mapper
public interface InfoMapper extends BaseMapper<Info> {}
