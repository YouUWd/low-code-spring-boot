package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleNodeDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModuleField;
import com.jdec.platform.config.biz.entity.SysTableRelation;
import com.jdec.platform.config.biz.mapper.SysModuleFieldMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.mapper.SysTableRelationMapper;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 统一配置开放接口服务实现
 *
 * <p>读取 config_engine 核心配置表（sys_module, sys_module_field, sys_table_relation）。 提供原子化查询能力，以及内存组合构建的
 * getModuleComplete。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class ConfigServiceImpl implements ConfigApi {

    private final SysModuleMapper sysModuleMapper;
    private final SysModuleFieldMapper sysModuleFieldMapper;
    private final SysTableRelationMapper sysTableRelationMapper;

    @Override
    public ModuleNodeDTO getModuleBasic(Long moduleId) {
        if (moduleId == null) {
            return null;
        }
        SysModule module = sysModuleMapper.selectById(moduleId);
        if (module == null) {
            return null;
        }
        return toModuleNodeDTO(module);
    }

    @Override
    public ModuleNodeDTO getModuleBasic(String projectNo, Long subjectId, Long moduleId) {
        if (moduleId == null) {
            return null;
        }
        SysModule module = sysModuleMapper.selectById(moduleId);
        if (module == null
                || (projectNo != null && !Objects.equals(module.getProjectNo(), projectNo))
                || (subjectId != null && !Objects.equals(module.getSubjectId(), subjectId))) {
            return null;
        }
        return toModuleNodeDTO(module);
    }

    @Override
    public List<ModuleNodeDTO> listModulesByProject(String projectNo) {
        List<SysModule> modules =
                sysModuleMapper.selectList(
                        Wrappers.<SysModule>lambdaQuery()
                                .eq(
                                        projectNo != null && !projectNo.isBlank(),
                                        SysModule::getProjectNo,
                                        projectNo)
                                .orderByAsc(SysModule::getSortOrder));
        if (modules == null || modules.isEmpty()) {
            return Collections.emptyList();
        }
        return modules.stream().map(this::toModuleNodeDTO).collect(Collectors.toList());
    }

    @Override
    public List<ModuleFieldDTO> listFieldsByModuleId(Long moduleId) {
        if (moduleId == null) {
            return Collections.emptyList();
        }
        List<SysModuleField> fields =
                sysModuleFieldMapper.selectList(
                        Wrappers.<SysModuleField>lambdaQuery()
                                .eq(SysModuleField::getModuleId, moduleId)
                                .orderByAsc(SysModuleField::getSortOrder));
        if (fields == null || fields.isEmpty()) {
            return Collections.emptyList();
        }
        return fields.stream().map(this::toModuleFieldDTO).collect(Collectors.toList());
    }

    @Override
    public List<ModuleFieldDTO> listFieldsByIds(List<Long> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysModuleField> fields =
                sysModuleFieldMapper.selectList(
                        Wrappers.<SysModuleField>lambdaQuery().in(SysModuleField::getId, fieldIds));
        if (fields == null || fields.isEmpty()) {
            return Collections.emptyList();
        }
        return fields.stream().map(this::toModuleFieldDTO).collect(Collectors.toList());
    }

    @Override
    public List<TableRelationDTO> listTableRelations(
            String projectNo, Collection<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> lowerTables =
                tableNames.stream()
                        .filter(t -> t != null && !t.isBlank())
                        .map(t -> t.trim().toLowerCase())
                        .collect(Collectors.toSet());
        if (lowerTables.isEmpty()) {
            return Collections.emptyList();
        }

        List<SysTableRelation> relations =
                sysTableRelationMapper.selectList(
                        Wrappers.<SysTableRelation>lambdaQuery()
                                .eq(
                                        projectNo != null && !projectNo.isBlank(),
                                        SysTableRelation::getProjectNo,
                                        projectNo)
                                .and(
                                        w ->
                                                w.in(SysTableRelation::getMainTable, lowerTables)
                                                        .or()
                                                        .in(
                                                                SysTableRelation::getJoinTable,
                                                                lowerTables)));

        if (relations == null || relations.isEmpty()) {
            return Collections.emptyList();
        }

        return relations.stream()
                .filter(
                        r -> {
                            String mainT =
                                    r.getMainTable() != null
                                            ? r.getMainTable().trim().toLowerCase()
                                            : "";
                            String joinT =
                                    r.getJoinTable() != null
                                            ? r.getJoinTable().trim().toLowerCase()
                                            : "";
                            return lowerTables.contains(mainT) && lowerTables.contains(joinT);
                        })
                .map(this::toTableRelationDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ModuleInfo getModuleWithFields(String projectNo, Long subjectId, Long moduleId) {
        if (moduleId == null) {
            return null;
        }

        SysModule module = sysModuleMapper.selectById(moduleId);
        if (module == null) {
            return null;
        }
        if (projectNo != null && !Objects.equals(module.getProjectNo(), projectNo)) {
            return null;
        }
        if (subjectId != null && !Objects.equals(module.getSubjectId(), subjectId)) {
            return null;
        }

        List<ModuleFieldDTO> fieldInfos = listFieldsByModuleId(moduleId);

        return ModuleInfo.builder()
                .moduleId(module.getId())
                .parentId(module.getParentId() != null ? module.getParentId() : 0L)
                .moduleCode(module.getModuleCode())
                .moduleName(module.getModuleName())
                .moduleDesc(module.getModuleDesc())
                .primaryTable(module.getPrimaryTable())
                .fields(fieldInfos)
                .build();
    }

    private ModuleNodeDTO toModuleNodeDTO(SysModule m) {
        return ModuleNodeDTO.builder()
                .id(m.getId())
                .parentId(m.getParentId())
                .moduleCode(m.getModuleCode())
                .moduleName(m.getModuleName())
                .primaryTable(m.getPrimaryTable())
                .sortOrder(m.getSortOrder())
                .build();
    }

    private ModuleFieldDTO toModuleFieldDTO(SysModuleField f) {
        return ModuleFieldDTO.builder()
                .id(f.getId())
                .moduleId(f.getModuleId())
                .tableName(f.getTableName())
                .columnName(f.getColumnName())
                .displayName(f.getDisplayName())
                .sortOrder(f.getSortOrder())
                .build();
    }

    private TableRelationDTO toTableRelationDTO(SysTableRelation r) {
        return TableRelationDTO.builder()
                .id(r.getId())
                .mainTable(r.getMainTable())
                .mainField(r.getMainField())
                .joinTable(r.getJoinTable())
                .joinField(r.getJoinField())
                .relationType(r.getRelationType())
                .description(r.getDescription())
                .build();
    }
}
