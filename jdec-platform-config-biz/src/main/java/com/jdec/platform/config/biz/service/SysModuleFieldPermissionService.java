package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysModuleFieldPermissionApi;
import com.jdec.platform.config.api.constant.ConfigConstants;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.config.biz.entity.SysField;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModuleField;
import com.jdec.platform.config.biz.entity.SysRoleModuleFieldPermission;
import com.jdec.platform.config.biz.mapper.SysFieldMapper;
import com.jdec.platform.config.biz.mapper.SysModuleFieldMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
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

/** 模块字段管理业务实现类 负责高性能模块、表与字段纯净物理树的获取 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_ENGINE)
public class SysModuleFieldPermissionService implements SysModuleFieldPermissionApi {

    private final SysModuleMapper sysModuleMapper;
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

        // 1. 【第一层】批量加载当前项目+主体下的所有模块
        List<SysModule> modules = queryModules(projectNo, subjectId);
        if (modules.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 【准备阶段】批量加载并构建上下文 Context（包含表/列元数据、字典及只读信息）
        List<Long> moduleIds = modules.stream().map(SysModule::getId).toList();
        TreeBuildContext context = buildTreeContext(projectNo, subjectId, moduleIds);

        // 3. 【第二层 & 第三层】层层递进构造：模块 -> 表 -> 列/字段节点列表
        return modules.stream().map(m -> buildModuleNode(m, context)).toList();
    }

    /** 模块查询（第一层数据源） */
    private List<SysModule> queryModules(String projectNo, Long subjectId) {
        LambdaQueryWrapper<SysModule> moduleWrapper = new LambdaQueryWrapper<>();
        moduleWrapper
                .eq(SysModule::getProjectNo, projectNo)
                .eq(SysModule::getSubjectId, subjectId)
                .orderByAsc(SysModule::getSortOrder);
        return sysModuleMapper.selectList(moduleWrapper);
    }

    /** 构建第一层：模块节点信息（包含下挂的表节点） */
    private SysModuleFieldTreeResp buildModuleNode(SysModule m, TreeBuildContext context) {
        SysModuleFieldTreeResp dto = new SysModuleFieldTreeResp();
        dto.setModuleId(m.getId());
        dto.setModuleName(m.getModuleName());
        dto.setModuleCode(m.getModuleCode());

        // 层层递进构造下属的表及字段树
        dto.setTables(buildTableNodes(m.getId(), context));
        return dto;
    }

    /** 构建第二层：模块包含的表节点列表 */
    private List<ModuleTableTreeResp> buildTableNodes(Long moduleId, TreeBuildContext context) {
        List<ModuleTableTreeResp> tablesList = new ArrayList<>();

        Map<String, List<SysModuleField>> tableFieldsMap =
                context.fieldsByModuleTable.getOrDefault(moduleId, Collections.emptyMap());

        // 从字段列表提取所涉及的所有物理表
        for (Map.Entry<String, List<SysModuleField>> entry : tableFieldsMap.entrySet()) {
            String tableName = entry.getKey();
            List<SysModuleField> fieldsInTable = entry.getValue();
            if (fieldsInTable == null || fieldsInTable.isEmpty()) {
                continue;
            }

            List<ModuleFieldTreeResp> columnNodes =
                    buildColumnNodesForTable(tableName, fieldsInTable, context);
            if (!columnNodes.isEmpty()) {
                ModuleTableTreeResp tableResp = new ModuleTableTreeResp();
                tableResp.setTableName(tableName);
                tableResp.setTableType(ConfigConstants.TABLE_TYPE_SIMPLE);
                tableResp.setReadOnly(0);

                String tDesc = context.tableCommentMap.get(tableName.toLowerCase());
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

        for (SysModuleField sf : tableFields) {
            ModuleFieldTreeResp fieldResp = new ModuleFieldTreeResp();
            fieldResp.setId(sf.getId());
            fieldResp.setFieldCode(sf.getColumnName());

            String lowerFieldCode =
                    sf.getColumnName() != null ? sf.getColumnName().toLowerCase() : "";

            // 计算显示名称：sys_module_field.displayName > sys_field 字典翻译 > 物理注释 > 默认列名
            String displayName = sf.getDisplayName();
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = tableNameTranslation.get(lowerFieldCode);
            }
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = physicalComments.get(lowerFieldCode);
            }
            fieldResp.setFieldName(displayName != null ? displayName : sf.getColumnName());

            fields.add(fieldResp);
        }
        return fields;
    }

    /** 树形构建上下文：封装批量预加载的数据字典与 Mapping 关系 */
    private TreeBuildContext buildTreeContext(
            String projectNo, Long subjectId, List<Long> moduleIds) {
        TreeBuildContext context = new TreeBuildContext();

        // 1. 批量拉取模块关联字段，建立双层 LinkedHashMap: moduleId -> (tableName -> List<SysModuleField>)
        LambdaQueryWrapper<SysModuleField> fieldWrapper = new LambdaQueryWrapper<>();
        fieldWrapper
                .in(SysModuleField::getModuleId, moduleIds)
                .orderByAsc(SysModuleField::getSortOrder);
        List<SysModuleField> allFields = sysModuleFieldMapper.selectList(fieldWrapper);

        context.fieldsByModuleTable =
                allFields.stream()
                        .filter(f -> f.getTableName() != null && !f.getTableName().trim().isEmpty())
                        .collect(
                                Collectors.groupingBy(
                                        SysModuleField::getModuleId,
                                        Collectors.groupingBy(
                                                SysModuleField::getTableName,
                                                LinkedHashMap::new,
                                                Collectors.toList())));

        Set<String> allTableNames =
                allFields.stream()
                        .map(SysModuleField::getTableName)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(name -> !name.isEmpty())
                        .collect(Collectors.toSet());

        // 2. 动态路由物理库，批量加载物理表/列的数据库注释
        loadPhysicalComments(
                projectNo,
                allTableNames,
                context.tableCommentMap,
                context.physicalColumnCommentMap);

        // 3. 批量加载逻辑字典 sys_field 展示名字
        loadSysFieldTranslations(projectNo, subjectId, allTableNames, context.translationMap);

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

    /** 批量加载逻辑字典 sys_field 展示名字 */
    private void loadSysFieldTranslations(
            String projectNo,
            Long subjectId,
            Set<String> allTableNames,
            Map<String, Map<String, String>> translationMap) {
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
            }
        }
    }

    /** 树形构建上下文封装对象 */
    private static class TreeBuildContext {
        Map<Long, Map<String, List<SysModuleField>>> fieldsByModuleTable = new HashMap<>();
        Map<String, String> tableCommentMap = new HashMap<>();
        Map<String, Map<String, String>> physicalColumnCommentMap = new HashMap<>();
        Map<String, Map<String, String>> translationMap = new HashMap<>();
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

        // 2. 获取该模块下的所有字段
        List<SysModuleField> allFields =
                sysModuleFieldMapper.selectList(
                        Wrappers.<SysModuleField>lambdaQuery()
                                .eq(SysModuleField::getModuleId, moduleId)
                                .orderByAsc(SysModuleField::getSortOrder));

        Set<String> allTableNames =
                allFields.stream()
                        .map(SysModuleField::getTableName)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(name -> !name.isEmpty())
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        // 3. 批量加载表/列物理注释与字典翻译
        Map<String, String> tableCommentMap = new HashMap<>();
        Map<String, Map<String, String>> physicalColumnCommentMap = new HashMap<>();
        loadPhysicalComments(projectNo, allTableNames, tableCommentMap, physicalColumnCommentMap);

        Map<String, Map<String, String>> translationMap = new HashMap<>();
        loadSysFieldTranslations(projectNo, subjectId, allTableNames, translationMap);

        // 4. 查询该角色模块下的字段权限记录，建立 (tableName.columnName) -> Entity 映射 Map
        List<SysRoleModuleFieldPermission> permissions =
                sysRoleModuleFieldPermissionMapper.selectList(
                        Wrappers.<SysRoleModuleFieldPermission>lambdaQuery()
                                .eq(SysRoleModuleFieldPermission::getRoleId, roleId)
                                .eq(SysRoleModuleFieldPermission::getModuleId, moduleId));
        Map<String, SysRoleModuleFieldPermission> permissionMap =
                permissions.stream()
                        .filter(p -> p.getTableName() != null && p.getColumnName() != null)
                        .collect(
                                Collectors.toMap(
                                        p ->
                                                (p.getTableName() + "." + p.getColumnName())
                                                        .toLowerCase(),
                                        p -> p,
                                        (p1, p2) -> p1));

        // 5. 按物理表为基准进行字段分组与权限装配
        List<PermissionTableGroupResp> tablesList = new ArrayList<>();

        for (String tableName : allTableNames) {
            PermissionTableGroupResp tableResp = new PermissionTableGroupResp();
            tableResp.setTableName(tableName);
            tableResp.setTableType(ConfigConstants.TABLE_TYPE_SIMPLE);

            String tDesc = tableCommentMap.get(tableName.toLowerCase());
            tableResp.setTableDesc((tDesc != null && !tDesc.trim().isEmpty()) ? tDesc : tableName);

            List<PermissionFieldInfo> readableFields = new ArrayList<>();
            List<PermissionFieldInfo> writableFields = new ArrayList<>();
            List<PermissionFieldInfo> updatableFields = new ArrayList<>();

            List<SysModuleField> fieldsInTable =
                    allFields.stream()
                            .filter(f -> tableName.equalsIgnoreCase(f.getTableName()))
                            .toList();

            for (SysModuleField f : fieldsInTable) {
                String colName = f.getColumnName();
                String translatedName = f.getDisplayName();
                if (translatedName == null || translatedName.trim().isEmpty()) {
                    Map<String, String> colMap = translationMap.get(tableName.toLowerCase());
                    if (colMap != null && colName != null) {
                        translatedName = colMap.get(colName.toLowerCase());
                    }
                }
                if (translatedName == null || translatedName.trim().isEmpty()) {
                    Map<String, String> physMap =
                            physicalColumnCommentMap.get(tableName.toLowerCase());
                    if (physMap != null && colName != null) {
                        translatedName = physMap.get(colName.toLowerCase());
                    }
                }
                if (translatedName == null || translatedName.trim().isEmpty()) {
                    translatedName = colName;
                }

                PermissionFieldInfo fieldInfo =
                        PermissionFieldInfo.builder()
                                .id(f.getId())
                                .fieldCode(colName)
                                .fieldName(translatedName)
                                .build();

                String permKey = (tableName + "." + colName).toLowerCase();
                SysRoleModuleFieldPermission perm = permissionMap.get(permKey);
                if (perm != null) {
                    if (Integer.valueOf(1).equals(perm.getView())) {
                        readableFields.add(fieldInfo);
                    }
                    if (Integer.valueOf(1).equals(perm.getApply())) {
                        writableFields.add(fieldInfo);
                    }
                    if (Integer.valueOf(1).equals(perm.getEdit())) {
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
