package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleNodeDTO;
import com.jdec.platform.config.api.dto.common.ModulePermissionDTO;
import com.jdec.platform.config.api.dto.common.ModulePermissionDTO.FieldPermissionItem;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModuleField;
import com.jdec.platform.config.biz.entity.SysRoleModuleFieldPermission;
import com.jdec.platform.config.biz.entity.SysTableRelation;
import com.jdec.platform.config.biz.mapper.SysModuleFieldMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.mapper.SysRoleModuleFieldPermissionMapper;
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
    private final SysRoleModuleFieldPermissionMapper sysRoleModuleFieldPermissionMapper;

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

    @Override
    public Map<Long, ModulePermissionDTO> getModulePermissions(
            Long roleId, Collection<Long> moduleIds) {
        if (roleId == null || moduleIds == null || moduleIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 1. 查询所有模块以构建模块父子拓扑
        List<SysModule> allModules = sysModuleMapper.selectList(Wrappers.emptyWrapper());
        if (allModules == null || allModules.isEmpty()) {
            return Collections.emptyMap();
        }

        // parentId -> List<SysModule>
        Map<Long, List<SysModule>> childrenMap =
                allModules.stream()
                        .filter(m -> m.getParentId() != null)
                        .collect(Collectors.groupingBy(SysModule::getParentId));

        // 2. 对每个传入的 moduleId，收集其自身及所有子孙模块 ID 集合
        Map<Long, Set<Long>> moduleFamilyMap = new LinkedHashMap<>();
        Set<Long> allRelevantModuleIds = new HashSet<>();

        for (Long rootModId : moduleIds) {
            if (rootModId == null) {
                continue;
            }
            Set<Long> family = new LinkedHashSet<>();
            family.add(rootModId);
            collectDescendantModuleIds(rootModId, childrenMap, family);
            moduleFamilyMap.put(rootModId, family);
            allRelevantModuleIds.addAll(family);
        }

        if (allRelevantModuleIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 3. 批量查询涉及的所有模块在对应 roleId 下的字段权限配置记录
        List<SysRoleModuleFieldPermission> permissions =
                sysRoleModuleFieldPermissionMapper.selectList(
                        Wrappers.<SysRoleModuleFieldPermission>lambdaQuery()
                                .eq(SysRoleModuleFieldPermission::getRoleId, roleId)
                                .in(
                                        SysRoleModuleFieldPermission::getModuleId,
                                        allRelevantModuleIds));

        // moduleId -> List<SysRoleModuleFieldPermission>
        Map<Long, List<SysRoleModuleFieldPermission>> permMapByModuleId =
                (permissions != null)
                        ? permissions.stream()
                                .collect(
                                        Collectors.groupingBy(
                                                SysRoleModuleFieldPermission::getModuleId))
                        : Collections.emptyMap();

        // 4. 为每个请求的 moduleId 构造 ModulePermissionDTO (涵盖自身及子孙模块的全部字段权限)
        Map<Long, ModulePermissionDTO> result = new LinkedHashMap<>();

        for (Map.Entry<Long, Set<Long>> entry : moduleFamilyMap.entrySet()) {
            Long targetModuleId = entry.getKey();
            Set<Long> familyModuleIds = entry.getValue();

            boolean modApply = false;
            boolean modView = false;
            boolean modEdit = false;

            Map<Long, FieldPermissionItem> fieldPermMap = new LinkedHashMap<>();

            for (Long subModId : familyModuleIds) {
                List<SysRoleModuleFieldPermission> subPerms =
                        permMapByModuleId.getOrDefault(subModId, Collections.emptyList());

                for (SysRoleModuleFieldPermission p : subPerms) {
                    if (p.getModuleFieldId() == null) {
                        continue;
                    }
                    boolean itemApply = p.getApply() != null && p.getApply() == 1;
                    boolean itemView = p.getView() != null && p.getView() == 1;
                    boolean itemEdit = p.getEdit() != null && p.getEdit() == 1;

                    if (itemApply) {
                        modApply = true;
                    }
                    if (itemView) {
                        modView = true;
                    }
                    if (itemEdit) {
                        modEdit = true;
                    }

                    FieldPermissionItem item =
                            FieldPermissionItem.builder()
                                    .fieldId(p.getModuleFieldId())
                                    .moduleId(subModId)
                                    .apply(itemApply)
                                    .view(itemView)
                                    .edit(itemEdit)
                                    .build();

                    fieldPermMap.put(p.getModuleFieldId(), item);
                }
            }

            ModulePermissionDTO dto =
                    ModulePermissionDTO.builder()
                            .moduleId(targetModuleId)
                            .apply(modApply)
                            .view(modView)
                            .edit(modEdit)
                            .fieldPermissions(fieldPermMap)
                            .build();

            result.put(targetModuleId, dto);
        }

        return result;
    }

    private void collectDescendantModuleIds(
            Long parentId, Map<Long, List<SysModule>> childrenMap, Set<Long> result) {
        List<SysModule> children = childrenMap.get(parentId);
        if (children == null || children.isEmpty()) {
            return;
        }
        for (SysModule child : children) {
            if (child.getId() != null && result.add(child.getId())) {
                collectDescendantModuleIds(child.getId(), childrenMap, result);
            }
        }
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
