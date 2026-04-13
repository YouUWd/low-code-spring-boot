package com.jdec.platform.config.biz.converter;

import com.jdec.platform.config.api.dto.response.ModulePermissionResp;
import com.jdec.platform.config.api.dto.response.RoleEntryResp;
import com.jdec.platform.config.api.dto.response.UserRoleEntryResp;
import com.jdec.platform.config.biz.entity.SysModulePermission;
import com.jdec.platform.config.biz.entity.SysRole;
import com.jdec.platform.config.biz.entity.SysUserRole;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 权限转换器 (BeanUtils 映射) */
@Component
public class PermissionConverter {

    // ==================== 权限转换 ====================

    /** Entity 转 Resp DTO */
    public ModulePermissionResp toModulePermissionResp(SysModulePermission entity) {
        if (entity == null) {
            return null;
        }
        return ModulePermissionResp.builder()
                .id(entity.getId())
                .moduleId(entity.getModuleId())
                .fieldCode(entity.getFieldCode())
                .permissionType(entity.getPermissionType())
                .permissionCode(entity.getPermissionCode())
                .permissionName(entity.getPermissionName())
                .description(entity.getDescription())
                .isEnabled(entity.getIsEnabled())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    /** Entity 列表转 Resp DTO 列表 */
    public List<ModulePermissionResp> toModulePermissionResps(List<SysModulePermission> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream().map(this::toModulePermissionResp).collect(Collectors.toList());
    }

    // ==================== 角色转换 ====================

    /** Entity 转 DTO */
    public RoleEntryResp toRoleEntry(SysRole entity) {
        if (entity == null) {
            return null;
        }
        return RoleEntryResp.builder()
                .id(entity.getId())
                .code(entity.getRoleCode())
                .name(entity.getRoleName())
                .description(entity.getDescription())
                .isEnabled(entity.getIsEnabled())
                .build();
    }

    /** Entity 列表转 DTO 列表 */
    public List<RoleEntryResp> toRoleEntries(List<SysRole> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream().map(this::toRoleEntry).collect(Collectors.toList());
    }

    /** DTO 转 Entity */
    public SysRole toSysRole(RoleEntryResp dto) {
        if (dto == null) {
            return null;
        }
        return SysRole.builder()
                .id(dto.getId())
                .roleCode(dto.getCode())
                .roleName(dto.getName())
                .description(dto.getDescription())
                .isEnabled(dto.getIsEnabled())
                .build();
    }

    // ==================== 用户角色关联转换 ====================

    /** Entity 转 Resp DTO */
    public UserRoleEntryResp toUserRoleEntryResp(SysUserRole entity) {
        if (entity == null) {
            return null;
        }
        return UserRoleEntryResp.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .roleId(entity.getRoleId())
                .createTime(entity.getCreateTime())
                .build();
    }

    /** Entity 列表转 Resp DTO 列表 */
    public List<UserRoleEntryResp> toUserRoleEntryResps(List<SysUserRole> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream().map(this::toUserRoleEntryResp).collect(Collectors.toList());
    }
}
