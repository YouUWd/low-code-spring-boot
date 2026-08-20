package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.config.biz.entity.SysRoleUser;
import org.apache.ibatis.annotations.Mapper;

/** 角色用户关联 Mapper */
@Mapper
public interface SysRoleUserMapper extends BaseMapper<SysRoleUser> {}
