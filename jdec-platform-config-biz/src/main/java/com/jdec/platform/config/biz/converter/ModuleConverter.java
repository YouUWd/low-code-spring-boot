package com.jdec.platform.config.biz.converter;

import com.jdec.platform.config.api.dto.request.ModuleFieldConfigReq;
import com.jdec.platform.config.api.dto.request.UserFieldConfigReq;
import com.jdec.platform.config.api.dto.response.ModuleEntryResp;
import com.jdec.platform.config.api.dto.response.ModuleFieldConfigResp;
import com.jdec.platform.config.api.dto.response.UserFieldConfigResp;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModuleFieldConfig;
import com.jdec.platform.config.biz.entity.SysUserFieldConfig;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 模块转换器 (BeanUtils 映射) */
@Component
public class ModuleConverter {

    // ==================== 模块转换 ====================

    /** Entity 转 DTO */
    public ModuleEntryResp toModuleEntry(SysModule entity) {
        if (entity == null) {
            return null;
        }
        return ModuleEntryResp.builder()
                .id(entity.getId())
                .moduleCode(entity.getModuleCode())
                .moduleName(entity.getModuleName())
                .enabled(entity.getEnabled())
                .description(entity.getDescription())
                .build();
    }

    /** Entity 列表转 DTO 列表 */
    public List<ModuleEntryResp> toModuleEntries(List<SysModule> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream().map(this::toModuleEntry).collect(Collectors.toList());
    }


    // ==================== 字段配置转换 ====================

    /** Entity 转 Resp DTO */
    public ModuleFieldConfigResp toModuleFieldConfigResp(SysModuleFieldConfig entity) {
        if (entity == null) {
            return null;
        }
        return ModuleFieldConfigResp.builder()
                .fieldCode(entity.getFieldCode())
                .fieldName(entity.getFieldName())
                .fieldType(entity.getFieldType())
                .visible(entity.getVisible())
                .sortOrder(entity.getSortOrder())
                .width(entity.getWidth())
                .align(entity.getAlign())
                .build();
    }

    /** Entity 列表转 Resp DTO 列表 */
    public List<ModuleFieldConfigResp> toModuleFieldConfigResps(
            List<SysModuleFieldConfig> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream().map(this::toModuleFieldConfigResp).collect(Collectors.toList());
    }

    /** Req DTO 转 Entity */
    public SysModuleFieldConfig toSysModuleFieldConfig(ModuleFieldConfigReq dto, Long moduleId) {
        if (dto == null) {
            return null;
        }
        return SysModuleFieldConfig.builder()
                .moduleId(moduleId)
                .fieldCode(dto.getFieldCode())
                .fieldName(dto.getFieldName())
                .fieldType(dto.getFieldType())
                .visible(dto.getVisible())
                .sortOrder(dto.getSortOrder())
                .width(dto.getWidth())
                .align(dto.getAlign())
                .build();
    }

    // ==================== 用户字段配置转换 ====================

    /** Entity 转 Resp DTO */
    public UserFieldConfigResp toUserFieldConfigResp(SysUserFieldConfig entity) {
        if (entity == null) {
            return null;
        }
        return UserFieldConfigResp.builder()
                .fieldCode(entity.getFieldCode())
                .visible(entity.getVisible())
                .sortOrder(entity.getSortOrder())
                .build();
    }

    /** Entity 列表转 Resp DTO 列表 */
    public List<UserFieldConfigResp> toUserFieldConfigResps(List<SysUserFieldConfig> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream().map(this::toUserFieldConfigResp).collect(Collectors.toList());
    }

    /** Req DTO 转 Entity */
    public SysUserFieldConfig toSysUserFieldConfig(
            UserFieldConfigReq dto, Long userId, Long moduleId) {
        if (dto == null) {
            return null;
        }
        return SysUserFieldConfig.builder()
                .userId(userId)
                .moduleId(moduleId)
                .fieldCode(dto.getFieldCode())
                .visible(dto.getVisible())
                .sortOrder(dto.getSortOrder())
                .build();
    }

    /** Req DTO 列表转 Entity 列表 */
    public List<SysUserFieldConfig> toSysUserFieldConfigs(
            List<UserFieldConfigReq> dtos, Long userId, Long moduleId) {
        if (dtos == null) {
            return Collections.emptyList();
        }
        return dtos.stream()
                .map(dto -> toSysUserFieldConfig(dto, userId, moduleId))
                .collect(Collectors.toList());
    }
}
