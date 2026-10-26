package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.config.biz.entity.BusinessFile;
import org.apache.ibatis.annotations.Mapper;

/** 业务文件 Mapper (动态路由至当前租户/项目对应的业务数据库) */
@Mapper
public interface BusinessFileMapper extends BaseMapper<BusinessFile> {}
