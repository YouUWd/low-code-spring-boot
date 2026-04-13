package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.config.biz.entity.SysRolePermission;
import org.apache.ibatis.annotations.Mapper;

/** 角色权限关联 Mapper */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {}
