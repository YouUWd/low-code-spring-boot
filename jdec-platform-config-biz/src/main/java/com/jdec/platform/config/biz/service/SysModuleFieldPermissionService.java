package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysModuleFieldPermissionApi;
import com.jdec.platform.config.api.constant.ConfigConstants;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.config.biz.entity.SysField;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModuleField;
import com.jdec.platform.config.biz.entity.SysModuleTable;
import com.jdec.platform.config.biz.entity.SysRoleModuleFieldPermission;
import com.jdec.platform.config.biz.mapper.SysFieldMapper;
import com.jdec.platform.config.biz.mapper.SysModuleFieldMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.mapper.SysModuleTableMapper;
import com.jdec.platform.config.biz.mapper.SysRoleModuleFieldPermissionMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import com.jdec.platform.shared.exception.BusinessException;
import java.sql.Connection;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 模块字段管理业务实现类 负责高性能模块、表与字段纯净物理树的获取 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysModuleFieldPermissionService implements SysModuleFieldPermissionApi {

    private final SysModuleMapper sysModuleMapper;
    private final SysModuleTableMapper sysModuleTableMapper;
    private final SysModuleFieldMapper sysModuleFieldMapper;
    private final SysFieldMapper sysFieldMapper;
    private final SysRoleModuleFieldPermissionMapper sysRoleModuleFieldPermissionMapper;
    private final DataSourceResolver dataSourceResolver;

    @Override
    public List<SysModuleFieldTreeResp> getModuleTableFieldTree(
            String projectNo, Long subjectId, Integer category) {
        log.info(
                "获取纯净模块表字段树: projectNo={}, subjectId={}, category={}",
                projectNo,
                subjectId,
                category);

        // 1. 【第一层】一量加载当前项目+主体下的所有模块
        List<SysModule> modules = queryModules(projectNo, subjectId, category);
        if (modules.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 【准备阶段】批量加载并构建上下文 Context（包含表/列元数据、字典及只读信息）
        List<Long> moduleIds = modules.stream().map(SysModule::getId).toList();
        TreeBuildContext context = buildTreeContext(projectNo, subjectId, moduleIds);

        // 3. 【第二层 & 第三层】层层递进构造：模块 -> 表 -> 列/字段节点列表
        List<SysModuleFieldTreeResp> dtoList =
                modules.stream().map(m -> buildModuleNode(m, context)).toList();

        // 4. 【树级联】双亲委派挂载子模块 children 树形结构
        return assembleModuleTree(dtoList, modules);
    }

    /** 模块查询（第一层数据源） */
    private List<SysModule> queryModules(String projectNo, Long subjectId, Integer category) {
        LambdaQueryWrapper<SysModule> moduleWrapper = new LambdaQueryWrapper<>();
        moduleWrapper
                .eq(SysModule::getProjectNo, projectNo)
                .eq(SysModule::getSubjectId, subjectId)
                .eq(category != null, SysModule::getCategory, category)
                .orderByAsc(SysModule::getParentId)
                .orderByAsc(SysModule::getSortOrder);
        return sysModuleMapper.selectList(moduleWrapper);
    }

    /** 构建第一层：模块节点信息（包含下挂的表节点） */
    private SysModuleFieldTreeResp buildModuleNode(SysModule m, TreeBuildContext context) {
        SysModuleFieldTreeResp dto = new SysModuleFieldTreeResp();
        dto.setModuleId(m.getId());
        dto.setModuleName(m.getModuleName());
        dto.setModuleCode(m.getModuleCode());
        dto.setModuleType(m.getModuleType());
        dto.setSourceSubjects(m.getSourceSubjects());
        dto.setBizDefFlag(m.getBizDefFlag());
        dto.setChildren(new ArrayList<>());

        // 层层递进构造下属的表及字段树
        dto.setTables(buildTableNodes(m.getId(), context));
        return dto;
    }

    /** 构建第二层：模块包含的表节点列表 */
    private List<ModuleTableTreeResp> buildTableNodes(Long moduleId, TreeBuildContext context) {
        List<ModuleTableTreeResp> tablesList = new ArrayList<>();

        List<SysModuleTable> moduleTables =
                context.tablesByModule.getOrDefault(moduleId, Collections.emptyList());
        Map<String, List<SysModuleField>> tableFieldsMap =
                context.fieldsByModuleTable.getOrDefault(moduleId, Collections.emptyMap());

        // 装配物理表及列信息（基于 sys_module_table 配置）
        for (SysModuleTable sysModuleTable : moduleTables) {
            String tableName = sysModuleTable.getTableName();
            if (tableName == null || tableName.trim().isEmpty()) {
                continue;
            }

            // 直接 O(1) 获取属于当前模块和当前表的字段列表
            List<SysModuleField> fieldsInTable =
                    tableFieldsMap.getOrDefault(tableName.toLowerCase(), Collections.emptyList());

            List<ModuleFieldTreeResp> columnNodes =
                    buildColumnNodesForTable(tableName, fieldsInTable, context);
            if (!columnNodes.isEmpty()) {
                ModuleTableTreeResp tableResp = new ModuleTableTreeResp();
                tableResp.setTableName(tableName);
                tableResp.setTableType(ConfigConstants.TABLE_TYPE_SIMPLE);
                tableResp.setReadOnly(
                        sysModuleTable.getReadOnly() != null ? sysModuleTable.getReadOnly() : 0);

                String tDesc = sysModuleTable.getTableDesc();
                if (tDesc == null || tDesc.trim().isEmpty()) {
                    tDesc = context.tableCommentMap.get(tableName.toLowerCase());
                }
                tableResp.setTableDesc(
                        tDesc != null && !tDesc.trim().isEmpty() ? tDesc : tableName);

                tableResp.setFields(columnNodes);
                tablesList.add(tableResp);
            }
        }

        return tablesList;
    }

    /** 构建第三层：物理表下的所有列/字段节点列表 */
    private List<ModuleFieldTreeResp> buildColumnNodesForTable(
            String tableName, List<SysModuleField> tableFields, TreeBuildContext context) {
        List<ModuleFieldTreeResp> fields = new ArrayList<>();
        Map<String, String> tableNameTranslation =
                context.translationMap.getOrDefault(
                        tableName.toLowerCase(), Collections.emptyMap());
        Map<String, String> physicalComments =
                context.physicalColumnCommentMap.getOrDefault(
                        tableName.toLowerCase(), Collections.emptyMap());
        Map<String, Integer> combineFlags =
                context.sysFieldCombineFlagMap.getOrDefault(
                        tableName.toLowerCase(), Collections.emptyMap());

        for (SysModuleField sf : tableFields) {
            ModuleFieldTreeResp fieldResp = new ModuleFieldTreeResp();
            fieldResp.setId(sf.getId());
            fieldResp.setFieldCode(sf.getFieldCode());

            String lowerFieldCode = sf.getFieldCode().toLowerCase();

            // 计算显示名称：sys_field 字典翻译 > 物理注释 > 默认字段编码
            String displayName = tableNameTranslation.get(lowerFieldCode);
            if (displayName == null) {
                displayName = physicalComments.get(lowerFieldCode);
            }
            fieldResp.setFieldName(displayName != null ? displayName : sf.getFieldCode());

            // 判定是否是组合字段：依据 sys_field 中的 combineInfo
            fieldResp.setCombineFlag(combineFlags.getOrDefault(lowerFieldCode, 0));
            fields.add(fieldResp);
        }
        return fields;
    }

    /** 双亲委派组装模块树 */
    private List<SysModuleFieldTreeResp> assembleModuleTree(
            List<SysModuleFieldTreeResp> dtoList, List<SysModule> modules) {
        Map<Long, SysModule> entityMap =
                modules.stream().collect(Collectors.toMap(SysModule::getId, m -> m));
        Map<Long, SysModuleFieldTreeResp> dtoMap =
                dtoList.stream()
                        .collect(Collectors.toMap(SysModuleFieldTreeResp::getModuleId, r -> r));
        List<SysModuleFieldTreeResp> rootList = new ArrayList<>();

        for (SysModuleFieldTreeResp dto : dtoList) {
            SysModule entity = entityMap.get(dto.getModuleId());
            if (entity.getParentId() == null || entity.getParentId() == 0) {
                rootList.add(dto);
            } else {
                SysModuleFieldTreeResp parentDto = dtoMap.get(entity.getParentId());
                if (parentDto != null) {
                    parentDto.getChildren().add(dto);
                } else {
                    rootList.add(dto);
                }
            }
        }
        return rootList;
    }

    /** 树形构建上下文：封装批量预加载的数据字典与 Mapping 关系 */
    private TreeBuildContext buildTreeContext(
            String projectNo, Long subjectId, List<Long> moduleIds) {
        TreeBuildContext context = new TreeBuildContext();

        // 1. 批量拉取模块关联表: moduleId -> List<SysModuleTable>
        LambdaQueryWrapper<SysModuleTable> moduleTableWrapper = new LambdaQueryWrapper<>();
        moduleTableWrapper
                .in(SysModuleTable::getModuleId, moduleIds)
                .orderByAsc(SysModuleTable::getSortOrder);
        List<SysModuleTable> moduleTables = sysModuleTableMapper.selectList(moduleTableWrapper);
        context.tablesByModule =
                moduleTables.stream()
                        .filter(t -> t.getTableName() != null)
                        .collect(Collectors.groupingBy(SysModuleTable::getModuleId));

        // 2. 批量拉取模块关联字段，建立双层 Map: moduleId -> (tableName.toLowerCase() -> List<SysModuleField>)
        LambdaQueryWrapper<SysModuleField> fieldWrapper = new LambdaQueryWrapper<>();
        fieldWrapper.in(SysModuleField::getModuleId, moduleIds);
        List<SysModuleField> allFields = sysModuleFieldMapper.selectList(fieldWrapper);

        context.fieldsByModuleTable =
                allFields.stream()
                        .filter(f -> f.getTableName() != null)
                        .collect(
                                Collectors.groupingBy(
                                        SysModuleField::getModuleId,
                                        Collectors.groupingBy(
                                                f -> f.getTableName().toLowerCase())));

        Set<String> allTableNames =
                moduleTables.stream()
                        .map(SysModuleTable::getTableName)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(name -> !name.isEmpty())
                        .collect(Collectors.toSet());

        // 3. 动态路由物理库，批量加载物理表/列的数据库注释
        loadPhysicalComments(
                projectNo,
                allTableNames,
                context.tableCommentMap,
                context.physicalColumnCommentMap);

        // 4. 批量加载逻辑字典 sys_field 展示名字与组合字段标识
        loadSysFieldTranslations(
                projectNo,
                subjectId,
                allTableNames,
                context.translationMap,
                context.sysFieldCombineFlagMap);

        return context;
    }

    /** 动态路由物理库，批量加载物理表/列的数据库注释 */
    private void loadPhysicalComments(
            String projectNo,
            Set<String> allTableNames,
            Map<String, String> tableCommentMap,
            Map<String, Map<String, String>> physicalColumnCommentMap) {
        if (allTableNames.isEmpty()) {
            return;
        }
        try {
            JdbcTemplate jdbcTemplate = dataSourceResolver.getJdbcTemplate(projectNo);

            String tableSchema = jdbcTemplate.execute((Connection conn) -> conn.getCatalog());
            if (tableSchema == null || tableSchema.trim().isEmpty()) {
                tableSchema = projectNo;
            }

            String tableInSql =
                    allTableNames.stream().map(t -> "?").collect(Collectors.joining(","));
            String tableCommentSql =
                    String.format(
                            "SELECT TABLE_NAME, TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME IN (%s)",
                            tableInSql);

            List<Object> tableParams = new ArrayList<>();
            tableParams.add(tableSchema);
            tableParams.addAll(allTableNames);

            List<Map<String, Object>> tableRows =
                    jdbcTemplate.queryForList(tableCommentSql, tableParams.toArray());
            for (Map<String, Object> row : tableRows) {
                String tName = (String) row.get("TABLE_NAME");
                String tComment = (String) row.get("TABLE_COMMENT");
                if (tName != null) {
                    tableCommentMap.put(tName.toLowerCase(), tComment);
                }
            }

            String columnCommentSql =
                    String.format(
                            "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_COMMENT FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME IN (%s)",
                            tableInSql);

            List<Map<String, Object>> columnRows =
                    jdbcTemplate.queryForList(columnCommentSql, tableParams.toArray());
            for (Map<String, Object> row : columnRows) {
                String tName = (String) row.get("TABLE_NAME");
                String cName = (String) row.get("COLUMN_NAME");
                String cComment = (String) row.get("COLUMN_COMMENT");
                if (tName != null && cName != null) {
                    physicalColumnCommentMap
                            .computeIfAbsent(tName.toLowerCase(), k -> new HashMap<>())
                            .put(cName.toLowerCase(), cComment);
                }
            }
        } catch (Exception e) {
            log.warn("批量获取物理库 [{}] 表和字段注释失败，将退回显示默认名称", projectNo, e);
        }
    }

    /** 批量加载逻辑字典 sys_field 展示名字与组合字段标识 */
    private void loadSysFieldTranslations(
            String projectNo,
            Long subjectId,
            Set<String> allTableNames,
            Map<String, Map<String, String>> translationMap,
            Map<String, Map<String, Integer>> sysFieldCombineFlagMap) {
        if (allTableNames.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<SysField> sysFieldWrapper = new LambdaQueryWrapper<>();
        sysFieldWrapper
                .eq(SysField::getProjectNo, projectNo)
                .eq(SysField::getSubjectId, subjectId)
                .eq(SysField::getDeleted, 0)
                .in(SysField::getTableName, allTableNames);
        List<SysField> sysFields = sysFieldMapper.selectList(sysFieldWrapper);

        for (SysField sf : sysFields) {
            if (sf.getTableName() != null && sf.getColumnName() != null) {
                if (sf.getDisplayName() != null && translationMap != null) {
                    translationMap
                            .computeIfAbsent(sf.getTableName().toLowerCase(), k -> new HashMap<>())
                            .put(sf.getColumnName().toLowerCase(), sf.getDisplayName());
                }
                if (sysFieldCombineFlagMap != null) {
                    int combineFlag = StringUtils.hasText(sf.getCombineInfo()) ? 1 : 0;
                    sysFieldCombineFlagMap
                            .computeIfAbsent(sf.getTableName().toLowerCase(), k -> new HashMap<>())
                            .put(sf.getColumnName().toLowerCase(), combineFlag);
                }
            }
        }
    }

    /** 树形构建上下文封装对象 */
    private static class TreeBuildContext {
        Map<Long, List<SysModuleTable>> tablesByModule = new HashMap<>();
        Map<Long, Map<String, List<SysModuleField>>> fieldsByModuleTable = new HashMap<>();
        Map<String, String> tableCommentMap = new HashMap<>();
        Map<String, Map<String, String>> physicalColumnCommentMap = new HashMap<>();
        Map<String, Map<String, String>> translationMap = new HashMap<>();
        Map<String, Map<String, Integer>> sysFieldCombineFlagMap = new HashMap<>();
    }

    @Override
    public List<PermissionTableGroupResp> getRoleModuleFieldPermissions(
            String projectNo, Long subjectId, Long roleId, Long moduleId) {
        log.info(
                "查询角色字段权限表级分组集合: projectNo={}, subjectId={}, roleId={}, moduleId={}",
                projectNo,
                subjectId,
                roleId,
                moduleId);

        // 1. 获取模块信息
        SysModule module = sysModuleMapper.selectById(moduleId);
        if (module == null
                || !Objects.equals(module.getProjectNo(), projectNo)
                || !Objects.equals(module.getSubjectId(), subjectId)) {
            throw new BusinessException(404, "模块不存在");
        }

        // 2. 查询该模块在 sys_module_table 配置的关联物理表
        List<SysModuleTable> moduleTables =
                sysModuleTableMapper.selectList(
                        Wrappers.<SysModuleTable>lambdaQuery()
                                .eq(SysModuleTable::getModuleId, moduleId)
                                .orderByAsc(SysModuleTable::getSortOrder));

        Set<String> allTableNames =
                moduleTables.stream()
                        .map(SysModuleTable::getTableName)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(name -> !name.isEmpty())
                        .collect(Collectors.toSet());

        // 3. 批量加载表/列物理注释与字典翻译
        Map<String, String> tableCommentMap = new HashMap<>();
        Map<String, Map<String, String>> physicalColumnCommentMap = new HashMap<>();
        loadPhysicalComments(projectNo, allTableNames, tableCommentMap, physicalColumnCommentMap);

        Map<String, Map<String, String>> translationMap = new HashMap<>();
        loadSysFieldTranslations(projectNo, subjectId, allTableNames, translationMap, null);

        // 4. 获取该模块下的所有字段
        List<SysModuleField> allFields =
                sysModuleFieldMapper.selectList(
                        Wrappers.<SysModuleField>lambdaQuery()
                                .eq(SysModuleField::getModuleId, moduleId));

        // 5. 查询该角色模块下的字段权限记录，建立 fieldId -> Entity 映射 Map
        List<SysRoleModuleFieldPermission> permissions =
                sysRoleModuleFieldPermissionMapper.selectList(
                        Wrappers.<SysRoleModuleFieldPermission>lambdaQuery()
                                .eq(SysRoleModuleFieldPermission::getRoleId, roleId)
                                .eq(SysRoleModuleFieldPermission::getModuleId, moduleId));
        Map<Long, SysRoleModuleFieldPermission> permissionMap =
                permissions.stream()
                        .collect(
                                Collectors.toMap(
                                        SysRoleModuleFieldPermission::getFieldId,
                                        p -> p,
                                        (p1, p2) -> p1));

        // 6. 按物理表为基准进行字段分组与权限装配
        List<PermissionTableGroupResp> tablesList = new ArrayList<>();

        for (SysModuleTable sysModuleTable : moduleTables) {
            String tableName = sysModuleTable.getTableName();
            if (tableName == null || tableName.trim().isEmpty()) {
                continue;
            }

            PermissionTableGroupResp tableResp = new PermissionTableGroupResp();
            tableResp.setTableName(tableName);
            tableResp.setTableType(ConfigConstants.TABLE_TYPE_SIMPLE);

            String tDesc = sysModuleTable.getTableDesc();
            if (tDesc == null || tDesc.trim().isEmpty()) {
                tDesc = tableCommentMap.get(tableName.toLowerCase());
            }
            tableResp.setTableDesc((tDesc != null && !tDesc.trim().isEmpty()) ? tDesc : tableName);

            List<PermissionFieldInfo> readableFields = new ArrayList<>();
            List<PermissionFieldInfo> writableFields = new ArrayList<>();
            List<PermissionFieldInfo> updatableFields = new ArrayList<>();

            List<SysModuleField> fieldsInTable =
                    allFields.stream()
                            .filter(f -> tableName.equalsIgnoreCase(f.getTableName()))
                            .toList();

            for (SysModuleField f : fieldsInTable) {
                String translatedName = null;
                Map<String, String> colMap = translationMap.get(tableName.toLowerCase());
                if (colMap != null) {
                    translatedName = colMap.get(f.getFieldCode().toLowerCase());
                }
                if (translatedName == null) {
                    Map<String, String> physMap =
                            physicalColumnCommentMap.get(tableName.toLowerCase());
                    if (physMap != null) {
                        translatedName = physMap.get(f.getFieldCode().toLowerCase());
                    }
                }
                if (translatedName == null || translatedName.trim().isEmpty()) {
                    translatedName = f.getDisplayName();
                }

                PermissionFieldInfo fieldInfo =
                        PermissionFieldInfo.builder()
                                .id(f.getId())
                                .fieldCode(f.getFieldCode())
                                .fieldName(translatedName)
                                .build();

                SysRoleModuleFieldPermission perm = permissionMap.get(f.getId());
                if (perm != null) {
                    if (Integer.valueOf(1).equals(perm.getReadable())) {
                        readableFields.add(fieldInfo);
                    }
                    if (Integer.valueOf(1).equals(perm.getWritable())) {
                        writableFields.add(fieldInfo);
                    }
                    if (Integer.valueOf(1).equals(perm.getUpdatable())) {
                        updatableFields.add(fieldInfo);
                    }
                }
            }

            tableResp.setReadableFields(readableFields);
            tableResp.setWritableFields(writableFields);
            tableResp.setUpdatableFields(updatableFields);
            tablesList.add(tableResp);
        }

        return tablesList;
    }
}
