package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.dto.common.FieldWordCloudDTO;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleNodeDTO;
import com.jdec.platform.config.api.dto.common.ModulePermissionDTO;
import com.jdec.platform.config.api.dto.common.ModulePermissionDTO.FieldPermissionItem;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.biz.entity.SysField;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModuleField;
import com.jdec.platform.config.biz.entity.SysRoleModuleFieldPermission;
import com.jdec.platform.config.biz.entity.SysTableRelation;
import com.jdec.platform.config.biz.mapper.SysFieldMapper;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
    private final SysFieldMapper sysFieldMapper;

    @Override
    @Cacheable(cacheNames = "config:module_tree", key = "#rootModuleId", unless = "#result == null")
    public ModuleTreeDTO getModuleTree(String projectNo, Long subjectId, Long rootModuleId) {
        log.info(
                "[Cache Miss -> DB Query] getModuleTree: projectNo={}, subjectId={}, rootModuleId={}",
                projectNo,
                subjectId,
                rootModuleId);
        if (rootModuleId == null) {
            return null;
        }

        ModuleNodeDTO rootBasic = getModuleBasic(projectNo, subjectId, rootModuleId);
        if (rootBasic == null) {
            return null;
        }

        String pNo = projectNo != null ? projectNo : rootBasic.getProjectNo();
        Long sId = subjectId != null ? subjectId : rootBasic.getSubjectId();

        List<SysModule> moduleList =
                sysModuleMapper.selectList(
                        Wrappers.<SysModule>lambdaQuery()
                                .eq(pNo != null && !pNo.isBlank(), SysModule::getProjectNo, pNo)
                                .eq(sId != null, SysModule::getSubjectId, sId)
                                .orderByAsc(SysModule::getSortOrder));

        if (moduleList == null || moduleList.isEmpty()) {
            return null;
        }

        Map<Long, SysModule> allModuleMap =
                moduleList.stream()
                        .filter(m -> m.getId() != null)
                        .collect(Collectors.toMap(SysModule::getId, m -> m, (k1, k2) -> k1));

        SysModule rootModule = allModuleMap.get(rootModuleId);
        if (rootModule == null) {
            return null;
        }

        Map<Long, List<SysModule>> globalChildrenMap =
                moduleList.stream()
                        .filter(m -> m.getParentId() != null)
                        .collect(Collectors.groupingBy(SysModule::getParentId));

        List<SysModule> subTreeModules = new ArrayList<>();
        subTreeModules.add(rootModule);
        collectDescendantSysModules(
                rootModuleId, globalChildrenMap, subTreeModules, new HashSet<>());

        Set<Long> allowedModuleIds = new LinkedHashSet<>();
        for (SysModule m : subTreeModules) {
            if (m.getId() != null) {
                allowedModuleIds.add(m.getId());
            }
        }

        // 批量一次性查询子树下所有模块的字段配置
        Map<Long, List<ModuleFieldDTO>> moduleFieldsMap = new LinkedHashMap<>();
        if (!allowedModuleIds.isEmpty()) {
            List<SysModuleField> allFields =
                    sysModuleFieldMapper.selectList(
                            Wrappers.<SysModuleField>lambdaQuery()
                                    .in(SysModuleField::getModuleId, allowedModuleIds)
                                    .orderByAsc(SysModuleField::getSortOrder));
            if (allFields != null && !allFields.isEmpty()) {
                moduleFieldsMap =
                        allFields.stream()
                                .filter(f -> f.getModuleId() != null)
                                .map(this::toModuleFieldDTO)
                                .collect(
                                        Collectors.groupingBy(
                                                ModuleFieldDTO::getModuleId,
                                                LinkedHashMap::new,
                                                Collectors.toList()));
            }
        }

        Map<Long, List<Long>> parentToChildren = new LinkedHashMap<>();
        Map<Long, ModuleInfo> moduleInfoMap = new LinkedHashMap<>();
        Map<Long, ModuleFieldDTO> fieldMap = new LinkedHashMap<>();
        Map<Long, Long> fieldToModuleMap = new LinkedHashMap<>();

        for (SysModule m : subTreeModules) {
            Long mid = m.getId();

            List<ModuleFieldDTO> fields =
                    moduleFieldsMap.getOrDefault(mid, Collections.emptyList());

            for (ModuleFieldDTO f : fields) {
                if (f != null && f.getId() != null) {
                    fieldMap.put(f.getId(), f);
                    fieldToModuleMap.put(f.getId(), mid);
                }
            }

            ModuleInfo minfo =
                    ModuleInfo.builder()
                            .moduleId(mid)
                            .parentId(m.getParentId())
                            .projectNo(m.getProjectNo())
                            .subjectId(m.getSubjectId())
                            .moduleCode(m.getModuleCode())
                            .moduleName(m.getModuleName())
                            .moduleDesc(m.getModuleDesc())
                            .primaryTable(m.getPrimaryTable())
                            .fields(fields)
                            .build();

            moduleInfoMap.put(mid, minfo);

            if (!mid.equals(rootModuleId)) {
                Long pid =
                        (m.getParentId() != null && m.getParentId() != 0L)
                                ? m.getParentId()
                                : rootModuleId;
                parentToChildren.computeIfAbsent(pid, k -> new ArrayList<>()).add(mid);
            }
        }

        ModuleInfo rootModuleInfo = moduleInfoMap.get(rootModuleId);

        ModuleTreeDTO treeDTO =
                ModuleTreeDTO.builder()
                        .rootModuleId(rootModuleId)
                        .projectNo(pNo)
                        .subjectId(sId)
                        .rootModule(rootModuleInfo)
                        .moduleMap(moduleInfoMap)
                        .parentToChildren(parentToChildren)
                        .fieldMap(fieldMap)
                        .fieldToModuleMap(fieldToModuleMap)
                        .allowedModuleIds(allowedModuleIds)
                        .build();

        log.info(
                "[getModuleTree] 构建模块树成功: rootModuleId={}, moduleCount={}, fieldCount={}",
                rootModuleId,
                moduleInfoMap.size(),
                fieldMap.size());

        return treeDTO;
    }

    private void collectDescendantSysModules(
            Long currentId,
            Map<Long, List<SysModule>> childrenMap,
            List<SysModule> result,
            Set<Long> visited) {
        if (!visited.add(currentId)) {
            return;
        }
        List<SysModule> children = childrenMap.get(currentId);
        if (children == null || children.isEmpty()) {
            return;
        }
        for (SysModule child : children) {
            if (child != null && child.getId() != null) {
                result.add(child);
                collectDescendantSysModules(child.getId(), childrenMap, result, visited);
            }
        }
    }

    @Cacheable(
            cacheNames = "config:module_basic",
            key = "#moduleId",
            unless =
                    "#result == null"
                            + " || (#projectNo != null && !#projectNo.equals(#result.projectNo))"
                            + " || (#subjectId != null && !#subjectId.equals(#result.subjectId))")
    public ModuleNodeDTO getModuleBasic(String projectNo, Long subjectId, Long moduleId) {
        log.info(
                "[Cache Miss -> DB Query] getModuleBasic: projectNo={}, subjectId={}, moduleId={}",
                projectNo,
                subjectId,
                moduleId);
        if (moduleId == null) {
            return null;
        }
        SysModule module = sysModuleMapper.selectById(moduleId);
        if (module == null) {
            log.warn("[getModuleBasic] 模块不存在: moduleId={}", moduleId);
            return null;
        }
        if (projectNo != null && !Objects.equals(module.getProjectNo(), projectNo)) {
            log.warn(
                    "[getModuleBasic] projectNo 校验不匹配: 请求={}, 数据库={}",
                    projectNo,
                    module.getProjectNo());
            return null;
        }
        if (subjectId != null && !Objects.equals(module.getSubjectId(), subjectId)) {
            log.warn(
                    "[getModuleBasic] subjectId 校验不匹配: 请求={}, 数据库={}",
                    subjectId,
                    module.getSubjectId());
            return null;
        }
        ModuleNodeDTO dto = toModuleNodeDTO(module);
        log.info("[getModuleBasic] 查询数据库成功: moduleId={}, dto={}", moduleId, dto);
        return dto;
    }

    @Override
    @Cacheable(
            cacheNames = "config:project_table_relations",
            key = "#projectNo != null ? #projectNo : ''",
            unless = "#result == null")
    public ProjectTableRelationsDTO getProjectTableRelations(String projectNo) {
        log.info("[Cache Miss -> DB Query] getProjectTableRelations: projectNo={}", projectNo);
        List<SysTableRelation> relations =
                sysTableRelationMapper.selectList(
                        Wrappers.<SysTableRelation>lambdaQuery()
                                .eq(
                                        projectNo != null && !projectNo.isBlank(),
                                        SysTableRelation::getProjectNo,
                                        projectNo));

        if (relations == null || relations.isEmpty()) {
            return ProjectTableRelationsDTO.builder()
                    .projectNo(projectNo)
                    .allRelations(Collections.emptyList())
                    .forwardRelations(Collections.emptyMap())
                    .reverseRelations(Collections.emptyMap())
                    .build();
        }

        List<TableRelationDTO> allDtoList =
                relations.stream().map(this::toTableRelationDTO).collect(Collectors.toList());

        Map<String, List<TableRelationDTO>> forwardMap = new HashMap<>();
        Map<String, List<TableRelationDTO>> reverseMap = new HashMap<>();

        for (TableRelationDTO rel : allDtoList) {
            if (rel == null) {
                continue;
            }
            if (rel.getMainTable() != null && !rel.getMainTable().isBlank()) {
                forwardMap
                        .computeIfAbsent(
                                rel.getMainTable().trim().toLowerCase(), k -> new ArrayList<>())
                        .add(rel);
            }
            if (rel.getJoinTable() != null && !rel.getJoinTable().isBlank()) {
                reverseMap
                        .computeIfAbsent(
                                rel.getJoinTable().trim().toLowerCase(), k -> new ArrayList<>())
                        .add(rel);
            }
        }

        log.info(
                "[getProjectTableRelations] 查询数据库成功: projectNo={}, relationCount={}",
                projectNo,
                allDtoList.size());

        return ProjectTableRelationsDTO.builder()
                .projectNo(projectNo)
                .allRelations(allDtoList)
                .forwardRelations(forwardMap)
                .reverseRelations(reverseMap)
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

            Map<Long, FieldPermissionItem> moduleTreeFieldPermMap = new LinkedHashMap<>();
            Map<Long, FieldPermissionItem> moduleFieldPermMap = new LinkedHashMap<>();

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

                    moduleTreeFieldPermMap.put(p.getModuleFieldId(), item);
                    if (Objects.equals(subModId, targetModuleId)) {
                        moduleFieldPermMap.put(p.getModuleFieldId(), item);
                    }
                }
            }

            ModulePermissionDTO dto =
                    ModulePermissionDTO.builder()
                            .moduleId(targetModuleId)
                            .apply(modApply)
                            .view(modView)
                            .edit(modEdit)
                            .moduleFieldPermissions(moduleFieldPermMap)
                            .moduleTreeFieldPermissions(moduleTreeFieldPermMap)
                            .fieldPermissions(moduleTreeFieldPermMap)
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
                .projectNo(m.getProjectNo())
                .subjectId(m.getSubjectId())
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

    @Override
    public List<FieldWordCloudDTO> getWordCloudFields(String projectNo, Long subjectId) {
        log.debug("查询项目 {} 主体 {} 的词云配置字段列表", projectNo, subjectId);

        // 仅查询配置了 relationBusinessNo 的字段，且只查询关注的列
        List<SysField> fields =
                sysFieldMapper.selectList(
                        new LambdaQueryWrapper<SysField>()
                                .select(
                                        SysField::getTableName,
                                        SysField::getColumnName,
                                        SysField::getRelationBusinessNo,
                                        SysField::getRelationName)
                                .eq(
                                        projectNo != null && !projectNo.isBlank(),
                                        SysField::getProjectNo,
                                        projectNo)
                                .eq(subjectId != null, SysField::getSubjectId, subjectId)
                                .isNotNull(SysField::getRelationBusinessNo)
                                .ne(SysField::getRelationBusinessNo, "")
                                .eq(SysField::getDeleted, 0));

        if (fields == null || fields.isEmpty()) {
            return Collections.emptyList();
        }

        return fields.stream()
                .map(
                        field ->
                                FieldWordCloudDTO.builder()
                                        .tableName(field.getTableName())
                                        .columnName(field.getColumnName())
                                        .relationBusinessNo(field.getRelationBusinessNo())
                                        .relationName(field.getRelationName())
                                        .build())
                .toList();
    }

    @Override
    @Caching(
            evict = {
                @CacheEvict(cacheNames = "config:module_basic", key = "#moduleId"),
                @CacheEvict(cacheNames = "config:module_tree", allEntries = true),
                @CacheEvict(cacheNames = "config:project_table_relations", allEntries = true)
            })
    public void clearModuleCache(Long moduleId) {
        log.info("已通过注解式缓存精准淘汰模块 {} 的元数据缓存及全量模块树缓存", moduleId);
    }

    @Override
    @Caching(
            evict = {
                @CacheEvict(cacheNames = "config:module_basic", allEntries = true),
                @CacheEvict(cacheNames = "config:module_tree", allEntries = true),
                @CacheEvict(cacheNames = "config:project_table_relations", allEntries = true)
            })
    public void clearMetaCache() {
        log.info("已通过注解式缓存全量清空所有配置元数据缓存");
    }
}
