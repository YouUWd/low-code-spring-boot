package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.config.biz.entity.SysRoleModuleFieldPermission;
import org.apache.ibatis.annotations.Mapper;

/** 角色模块字段权限配置Mapper */
@Mapper
public interface SysRoleModuleFieldPermissionMapper
        extends BaseMapper<SysRoleModuleFieldPermission> {}
