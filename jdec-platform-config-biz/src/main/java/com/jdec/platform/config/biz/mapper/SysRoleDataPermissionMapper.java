package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.config.biz.entity.SysRoleDataPermission;
import org.apache.ibatis.annotations.Mapper;

/** 角色-数据权限关联 Mapper */
@Mapper
public interface SysRoleDataPermissionMapper extends BaseMapper<SysRoleDataPermission> {}
