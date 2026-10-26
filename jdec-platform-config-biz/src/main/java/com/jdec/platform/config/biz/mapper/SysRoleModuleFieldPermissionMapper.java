package com.jdec.platform.config.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.config.biz.entity.SysRoleModuleFieldPermission;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 角色模块字段权限配置Mapper */
@Mapper
public interface SysRoleModuleFieldPermissionMapper
        extends BaseMapper<SysRoleModuleFieldPermission> {

    /**
     * 插入一条模块级权限记录（不细分到字段）。
     *
     * <p>模块级权限以 {@code field_id=0} + 空 {@code table_name}/{@code column_name} 表示，
     * 读写维度全部放开；用于角色与模块的整体绑定。
     */
    @Insert(
            "INSERT INTO sys_role_module_field_permission (role_id, module_id, field_id, table_name,"
                    + " column_name, readable, writable, updatable, apply, view, edit, deleted) "
                    + "VALUES (#{roleId}, #{moduleId}, 0, '', '', 1, 1, 1, 1, 1, 1, 0)")
    int insertModulePermission(@Param("roleId") Long roleId, @Param("moduleId") Long moduleId);
}
