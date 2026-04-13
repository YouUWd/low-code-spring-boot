package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.biz.entity.SysRole;
import com.jdec.platform.config.biz.mapper.SysRoleMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 角色服务实现 */
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysRoleService {

    private final SysRoleMapper roleMapper;

    /** 创建角色 */
    public SysRole createRole(SysRole role) {
        roleMapper.insert(role);
        return role;
    }

    /** 根据 ID 查询角色 */
    public SysRole getRoleById(Long id) {
        return roleMapper.selectById(id);
    }

    /** 根据角色编码查询角色 */
    public SysRole getRoleByCode(String roleCode) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRole::getRoleCode, roleCode);
        return roleMapper.selectOne(wrapper);
    }

    /** 查询所有角色 */
    public List<SysRole> getAllRoles() {
        return roleMapper.selectList(null);
    }

    /** 查询启用的角色 */
    public List<SysRole> getEnabledRoles() {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRole::getIsEnabled, 1);
        return roleMapper.selectList(wrapper);
    }

    /** 更新角色 */
    public SysRole updateRole(SysRole role) {
        roleMapper.updateById(role);
        return role;
    }

    /** 删除角色 */
    public void deleteRole(Long id) {
        roleMapper.deleteById(id);
    }
}
