package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.config.biz.entity.SysRoleInteractionPermission;
import org.apache.ibatis.annotations.Mapper;

/** 角色-交互权限关联 Mapper */
@Mapper
public interface SysRoleInteractionPermissionMapper
        extends BaseMapper<SysRoleInteractionPermission> {}
