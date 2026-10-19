package com.jdec.platform.externalobject.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.externalobject.biz.entity.BasicInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外部对象工商与法定资质表（Tab 1 基本信息） Mapper 接口
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Mapper
public interface BasicInfoMapper extends BaseMapper<BasicInfo> {}
