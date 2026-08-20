package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.config.biz.entity.SysRoleSpecialPermission;
import org.apache.ibatis.annotations.Mapper;

/** 角色-特殊权限关联 Mapper */
@Mapper
public interface SysRoleSpecialPermissionMapper extends BaseMapper<SysRoleSpecialPermission> {}
