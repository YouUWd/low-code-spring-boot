package com.jdec.platform.dataengine.biz.config.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.dataengine.biz.config.dto.ModuleFieldDTO;
import com.jdec.platform.dataengine.biz.config.dto.ModuleNodeDTO;
import com.jdec.platform.dataengine.biz.config.dto.SysModuleInfo;
import com.jdec.platform.dataengine.biz.config.entity.DataSysModule;
import com.jdec.platform.dataengine.biz.config.entity.DataSysModuleField;
import com.jdec.platform.dataengine.biz.config.entity.DataSysTableRelation;
import com.jdec.platform.dataengine.biz.config.mapper.DataSysModuleFieldMapper;
import com.jdec.platform.dataengine.biz.config.mapper.DataSysModuleMapper;
import com.jdec.platform.dataengine.biz.config.mapper.DataSysTableRelationMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 数据引擎专属的模块元数据读取服务
 *
 * <p>直接基于 MyBatis-Plus 读取 config_engine 核心配置表（sys_module, sys_module_field, sys_table_relation）。
 *
 * <p>以原子扁平的 SysModuleInfo 作为唯一元数据契约返回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class DataModuleConfigService {

    private final DataSysModuleMapper moduleMapper;
    private final DataSysModuleFieldMapper moduleFieldMapper;
    private final DataSysTableRelationMapper tableRelationMapper;

    /** 根据模块ID获取模块元数据 (纯数据引擎视角，仅提取必要配置) */
    public SysModuleInfo getModuleCompleteById(String projectNo, Long subjectId, Long moduleId) {
        if (moduleId == null) {
            return null;
        }

        DataSysModule module = moduleMapper.selectById(moduleId);
        if (module == null) {
            return null;
        }
        if (projectNo != null && !Objects.equals(module.getProjectNo(), projectNo)) {
            return null;
        }
        if (subjectId != null && !Objects.equals(module.getSubjectId(), subjectId)) {
            return null;
        }

        // 1. 获取模块字段配置 (sys_module_field)
        List<DataSysModuleField> allFields =
                moduleFieldMapper.selectList(
                        Wrappers.<DataSysModuleField>lambdaQuery()
                                .eq(DataSysModuleField::getModuleId, moduleId)
                                .orderByAsc(DataSysModuleField::getSortOrder));

        List<ModuleFieldDTO> fieldInfos =
                allFields.stream()
                        .map(
                                f ->
                                        ModuleFieldDTO.builder()
                                                .id(f.getId())
                                                .moduleId(f.getModuleId())
                                                .tableName(f.getTableName())
                                                .columnName(f.getColumnName())
                                                .displayName(f.getDisplayName())
                                                .sortOrder(f.getSortOrder())
                                                .build())
                        .collect(Collectors.toList());

        // 2. 结合项目内全部模块拓扑推导关联拓扑与子模块树
        List<DataSysModule> allProjectModules =
                moduleMapper.selectList(
                        Wrappers.<DataSysModule>lambdaQuery()
                                .eq(
                                        module.getProjectNo() != null,
                                        DataSysModule::getProjectNo,
                                        module.getProjectNo()));
        Map<Long, DataSysModule> moduleMap =
                allProjectModules.stream()
                        .collect(Collectors.toMap(DataSysModule::getId, m -> m, (k1, k2) -> k1));

        // 收集当前模块涉及的表
        Set<String> involvedTables = new HashSet<>();
        if (module.getPrimaryTable() != null && !module.getPrimaryTable().isBlank()) {
            involvedTables.add(module.getPrimaryTable().trim().toLowerCase());
        }
        for (ModuleFieldDTO f : fieldInfos) {
            if (f.getTableName() != null && !f.getTableName().isBlank()) {
                involvedTables.add(f.getTableName().trim().toLowerCase());
            }
        }

        // 提取下级所有子孙模块的 primaryTable，确保级联外键拓扑可连接
        List<ModuleNodeDTO> childModuleNodes = new ArrayList<>();
        for (DataSysModule m : allProjectModules) {
            if (m.getId() != null && !m.getId().equals(moduleId)) {
                Long curParent = m.getParentId();
                boolean isDescendant = false;
                while (curParent != null && curParent > 0) {
                    if (curParent.equals(moduleId)) {
                        isDescendant = true;
                        break;
                    }
                    DataSysModule parentNode = moduleMap.get(curParent);
                    curParent = (parentNode != null) ? parentNode.getParentId() : null;
                }
                if (isDescendant) {
                    childModuleNodes.add(
                            ModuleNodeDTO.builder()
                                    .id(m.getId())
                                    .parentId(m.getParentId())
                                    .moduleCode(m.getModuleCode())
                                    .moduleName(m.getModuleName())
                                    .primaryTable(m.getPrimaryTable())
                                    .sortOrder(m.getSortOrder())
                                    .build());
                    if (m.getPrimaryTable() != null && !m.getPrimaryTable().isBlank()) {
                        involvedTables.add(m.getPrimaryTable().trim().toLowerCase());
                    }
                }
            }
        }

        // 3. 查询涉及激活物理表的关联关系 (sys_table_relation)
        List<TableRelationDTO> relationDTOs = Collections.emptyList();
        if (!involvedTables.isEmpty()) {
            List<DataSysTableRelation> relations =
                    tableRelationMapper.selectList(
                            Wrappers.<DataSysTableRelation>lambdaQuery()
                                    .eq(
                                            module.getProjectNo() != null,
                                            DataSysTableRelation::getProjectNo,
                                            module.getProjectNo())
                                    .and(
                                            w ->
                                                    w.in(
                                                                    DataSysTableRelation
                                                                            ::getMainTable,
                                                                    involvedTables)
                                                            .or()
                                                            .in(
                                                                    DataSysTableRelation
                                                                            ::getJoinTable,
                                                                    involvedTables)));

            relationDTOs =
                    relations.stream()
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
                                        return involvedTables.contains(mainT)
                                                && involvedTables.contains(joinT);
                                    })
                            .map(
                                    r ->
                                            TableRelationDTO.builder()
                                                    .id(r.getId())
                                                    .mainTable(r.getMainTable())
                                                    .mainField(r.getMainField())
                                                    .joinTable(r.getJoinTable())
                                                    .joinField(r.getJoinField())
                                                    .relationType(r.getRelationType())
                                                    .description(r.getDescription())
                                                    .build())
                            .collect(Collectors.toList());
        }

        return SysModuleInfo.builder()
                .moduleId(module.getId())
                .parentId(module.getParentId() != null ? module.getParentId() : 0L)
                .moduleCode(module.getModuleCode())
                .moduleName(module.getModuleName())
                .moduleDesc(module.getModuleDesc())
                .primaryTable(module.getPrimaryTable())
                .fields(fieldInfos)
                .tableRelations(relationDTOs)
                .moduleNodes(childModuleNodes)
                .build();
    }

    /** 根据字段ID列表批量获取字段元数据 */
    public List<ModuleFieldDTO> listFieldsByIds(List<Long> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<DataSysModuleField> fields =
                moduleFieldMapper.selectList(
                        Wrappers.<DataSysModuleField>lambdaQuery()
                                .in(DataSysModuleField::getId, fieldIds));
        return fields.stream()
                .map(
                        f ->
                                ModuleFieldDTO.builder()
                                        .id(f.getId())
                                        .moduleId(f.getModuleId())
                                        .tableName(f.getTableName())
                                        .columnName(f.getColumnName())
                                        .displayName(f.getDisplayName())
                                        .sortOrder(f.getSortOrder())
                                        .build())
                .collect(Collectors.toList());
    }
}
