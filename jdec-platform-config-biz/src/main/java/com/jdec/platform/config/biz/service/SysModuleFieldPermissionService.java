package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysModuleFieldPermissionApi;
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
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysModuleFieldPermissionService implements SysModuleFieldPermissionApi {

    private final SysModuleMapper sysModuleMapper;
    private final SysModuleTableMapper sysModuleTableMapper;
    private final SysModuleFieldMapper sysModuleFieldMapper;
    private final SysFieldMapper sysFieldMapper;
    private final SysRoleModuleFieldPermissionMapper sysRoleModuleFieldPermissionMapper;
    private final DataSourceResolver dataSourceResolver;

    @Override
    public List<SysModuleFieldTreeResp> getModuleTableFieldTree(String projectNo, Long subjectId) {
        log.info("获取纯净模块表字段树: projectNo={}, subjectId={}", projectNo, subjectId);

        // 1. 一次性全量加载当前项目+主体下的所有模块
        LambdaQueryWrapper<SysModule> moduleWrapper = new LambdaQueryWrapper<>();
        moduleWrapper
                .eq(SysModule::getProjectNo, projectNo)
                .eq(SysModule::getSubjectId, subjectId)
                .orderByAsc(SysModule::getParentId)
                .orderByAsc(SysModule::getSortOrder);
        List<SysModule> modules = sysModuleMapper.selectList(moduleWrapper);

        if (modules.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> moduleIds = modules.stream().map(SysModule::getId).toList();

        // 2.2 批量拉取所有关联表的 readOnly 属性并构建 Map: moduleId -> (tableName -> readOnly)
        LambdaQueryWrapper<SysModuleTable> moduleTableWrapper = new LambdaQueryWrapper<>();
        moduleTableWrapper.in(SysModuleTable::getModuleId, moduleIds);
        List<SysModuleTable> moduleTables = sysModuleTableMapper.selectList(moduleTableWrapper);
        Map<Long, Map<String, Integer>> moduleTableReadOnlyMap =
                moduleTables.stream()
                        .filter(t -> t.getTableName() != null)
                        .collect(
                                Collectors.groupingBy(
                                        SysModuleTable::getModuleId,
                                        Collectors.toMap(
                                                t -> t.getTableName().toLowerCase(),
                                                t -> t.getReadOnly() != null ? t.getReadOnly() : 0,
                                                (v1, v2) -> v1)));

        // 2. 批量拉取所有模块下关联的字段配置
        LambdaQueryWrapper<SysModuleField> fieldWrapper = new LambdaQueryWrapper<>();
        fieldWrapper.in(SysModuleField::getModuleId, moduleIds);
        List<SysModuleField> allFields = sysModuleFieldMapper.selectList(fieldWrapper);

        // 3. 内存分流简单物理字段与复合组合字段
        List<SysModuleField> simpleFields =
                allFields.stream().filter(f -> "SIMPLE".equals(f.getFieldType())).toList();

        List<SysModuleField> combineFields =
                allFields.stream()
                        .filter(
                                f ->
                                        "COMBINE".equals(f.getFieldType())
                                                && Integer.valueOf(1).equals(f.getEnabled()))
                        .sorted(Comparator.comparing(SysModuleField::getId))
                        .toList();

        // 4. 提取涉及的物理表名并去重
        Set<String> allTableNames =
                simpleFields.stream()
                        .map(SysModuleField::getTableName)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(name -> !name.isEmpty())
                        .collect(Collectors.toSet());

        // 5. 动态路由物理库，批量加载物理表/列的数据库注释
        Map<String, String> tableCommentMap = new HashMap<>();
        Map<String, Map<String, String>> physicalColumnCommentMap = new HashMap<>();

        if (!allTableNames.isEmpty()) {
            try {
                javax.sql.DataSource physicalDs = dataSourceResolver.resolve(projectNo);
                JdbcTemplate jdbcTemplate = new JdbcTemplate(physicalDs);

                String tableSchema =
                        jdbcTemplate.execute((java.sql.Connection conn) -> conn.getCatalog());
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

        // 6. 批量加载逻辑字典 sys_field 展示名字
        Map<String, Map<String, String>> translationMap = new HashMap<>();
        if (!allTableNames.isEmpty()) {
            LambdaQueryWrapper<SysField> sysFieldWrapper = new LambdaQueryWrapper<>();
            sysFieldWrapper
                    .eq(SysField::getProjectNo, projectNo)
                    .eq(SysField::getSubjectId, subjectId)
                    .eq(SysField::getDeleted, 0)
                    .in(SysField::getTableName, allTableNames);
            List<SysField> sysFields = sysFieldMapper.selectList(sysFieldWrapper);

            for (SysField sf : sysFields) {
                if (sf.getTableName() != null
                        && sf.getColumnName() != null
                        && sf.getDisplayName() != null) {
                    translationMap
                            .computeIfAbsent(sf.getTableName().toLowerCase(), k -> new HashMap<>())
                            .put(sf.getColumnName().toLowerCase(), sf.getDisplayName());
                }
            }
        }

        // 7. 按模块 ID 对字段进行内存分组
        Map<Long, List<SysModuleField>> simpleFieldsByModule =
                simpleFields.stream().collect(Collectors.groupingBy(SysModuleField::getModuleId));
        Map<Long, List<SysModuleField>> combineFieldsByModule =
                combineFields.stream().collect(Collectors.groupingBy(SysModuleField::getModuleId));

        // 8. 内存拼装纯净的表和字段树
        Map<Long, SysModule> entityMap =
                modules.stream().collect(Collectors.toMap(SysModule::getId, m -> m));

        List<SysModuleFieldTreeResp> dtoList =
                modules.stream()
                        .map(
                                m -> {
                                    SysModuleFieldTreeResp dto = new SysModuleFieldTreeResp();
                                    dto.setModuleId(m.getId());
                                    dto.setModuleName(m.getModuleName());
                                    dto.setModuleCode(m.getModuleCode());
                                    dto.setSourceSubjects(m.getSourceSubjects());
                                    dto.setChildren(new ArrayList<>());

                                    List<SysModuleField> sFields =
                                            simpleFieldsByModule.getOrDefault(
                                                    m.getId(), Collections.emptyList());
                                    List<SysModuleField> cFields =
                                            combineFieldsByModule.getOrDefault(
                                                    m.getId(), Collections.emptyList());

                                    // 装配模块下的表和字段（单路，免克隆和权限判定）
                                    dto.setTables(
                                            buildTableTree(
                                                    m.getId(),
                                                    sFields,
                                                    cFields,
                                                    tableCommentMap,
                                                    physicalColumnCommentMap,
                                                    translationMap,
                                                    moduleTableReadOnlyMap));

                                    return dto;
                                })
                        .toList();

        // 9. 双亲委派挂载子模块
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

    private List<ModuleTableTreeResp> buildTableTree(
            Long moduleId,
            List<SysModuleField> sfields,
            List<SysModuleField> cfields,
            Map<String, String> tableCommentMap,
            Map<String, Map<String, String>> physicalColumnCommentMap,
            Map<String, Map<String, String>> translationMap,
            Map<Long, Map<String, Integer>> moduleTableReadOnlyMap) {

        List<ModuleTableTreeResp> tablesList = new ArrayList<>();

        // 装配简单表及字段
        List<String> orderedTables =
                sfields.stream()
                        .map(SysModuleField::getTableName)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        for (String tableName : orderedTables) {
            ModuleTableTreeResp tableResp = new ModuleTableTreeResp();
            tableResp.setTableName(tableName);
            tableResp.setTableType("SIMPLE");
            Map<String, Integer> readOnlyMap = moduleTableReadOnlyMap.get(moduleId);
            Integer readOnly =
                    (readOnlyMap != null)
                            ? readOnlyMap.getOrDefault(tableName.toLowerCase(), 0)
                            : 0;
            tableResp.setReadOnly(readOnly);

            String tComment = tableCommentMap.get(tableName.toLowerCase());
            tableResp.setTableDesc(
                    tComment != null && !tComment.trim().isEmpty() ? tComment : tableName);

            List<ModuleFieldTreeResp> fields = new ArrayList<>();
            for (SysModuleField sf : sfields) {
                if (tableName.equalsIgnoreCase(sf.getTableName())) {
                    ModuleFieldTreeResp fieldResp = new ModuleFieldTreeResp();
                    fieldResp.setId(sf.getId());
                    fieldResp.setFieldCode(sf.getFieldCode());

                    String displayName =
                            translationMap
                                    .getOrDefault(tableName.toLowerCase(), Collections.emptyMap())
                                    .get(sf.getFieldCode().toLowerCase());

                    if (displayName == null) {
                        displayName =
                                physicalColumnCommentMap
                                        .getOrDefault(
                                                tableName.toLowerCase(), Collections.emptyMap())
                                        .get(sf.getFieldCode().toLowerCase());
                    }

                    fieldResp.setFieldName(displayName != null ? displayName : sf.getFieldCode());
                    fields.add(fieldResp);
                }
            }

            if (!fields.isEmpty()) {
                tableResp.setFields(fields);
                tablesList.add(tableResp);
            }
        }

        // 装配虚拟复合表及字段
        if (!cfields.isEmpty()) {
            ModuleTableTreeResp combineTableResp = new ModuleTableTreeResp();
            combineTableResp.setTableName("*");
            combineTableResp.setTableDesc("组合字段");
            combineTableResp.setTableType("COMBINE");
            combineTableResp.setReadOnly(0);

            List<ModuleFieldTreeResp> fields = new ArrayList<>();
            for (SysModuleField cf : cfields) {
                ModuleFieldTreeResp fieldResp = new ModuleFieldTreeResp();
                fieldResp.setId(cf.getId());
                fieldResp.setFieldCode(cf.getFieldCode());
                fieldResp.setFieldName(cf.getDisplayName());
                fields.add(fieldResp);
            }

            if (!fields.isEmpty()) {
                combineTableResp.setFields(fields);
                tablesList.add(combineTableResp);
            }
        }

        return tablesList;
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

        // 2. 获取该模块下的所有简单物理字段与复合逻辑字段
        List<SysModuleField> allFields =
                sysModuleFieldMapper.selectList(
                        Wrappers.<SysModuleField>lambdaQuery()
                                .eq(SysModuleField::getModuleId, moduleId));

        List<SysModuleField> sfields =
                allFields.stream().filter(f -> "SIMPLE".equals(f.getFieldType())).toList();
        List<SysModuleField> cfields =
                allFields.stream()
                        .filter(
                                f ->
                                        "COMBINE".equals(f.getFieldType())
                                                && Integer.valueOf(1).equals(f.getEnabled()))
                        .toList();

        // 3. 高性能装配表名/列注释及字典翻译
        Set<String> allTableNames =
                sfields.stream()
                        .map(SysModuleField::getTableName)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(name -> !name.isEmpty())
                        .collect(Collectors.toSet());

        Map<String, String> tableCommentMap = new HashMap<>();
        Map<String, Map<String, String>> physicalColumnCommentMap = new HashMap<>();

        if (!allTableNames.isEmpty()) {
            try {
                javax.sql.DataSource physicalDs = dataSourceResolver.resolve(projectNo);
                JdbcTemplate jdbcTemplate = new JdbcTemplate(physicalDs);
                String tableSchema =
                        jdbcTemplate.execute((java.sql.Connection conn) -> conn.getCatalog());
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
                log.warn("批量加载表/列注释失败", e);
            }
        }

        Map<String, Map<String, String>> translationMap = new HashMap<>();
        if (!allTableNames.isEmpty()) {
            List<SysField> sysFields =
                    sysFieldMapper.selectList(
                            Wrappers.<SysField>lambdaQuery()
                                    .eq(SysField::getProjectNo, projectNo)
                                    .eq(SysField::getSubjectId, subjectId)
                                    .eq(SysField::getDeleted, 0)
                                    .in(SysField::getTableName, allTableNames));
            for (SysField sf : sysFields) {
                if (sf.getTableName() != null
                        && sf.getColumnName() != null
                        && sf.getDisplayName() != null) {
                    translationMap
                            .computeIfAbsent(sf.getTableName().toLowerCase(), k -> new HashMap<>())
                            .put(sf.getColumnName().toLowerCase(), sf.getDisplayName());
                }
            }
        }

        // 4. 查询该角色模块下的字段权限记录，建立 fieldId -> Entity 映射 Map
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

        // 5. 按表对字段进行分组装配
        List<PermissionTableGroupResp> tablesList = new ArrayList<>();

        // A. 装配简单物理表
        List<String> orderedTables =
                sfields.stream()
                        .map(SysModuleField::getTableName)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        for (String tableName : orderedTables) {
            PermissionTableGroupResp tableResp = new PermissionTableGroupResp();
            tableResp.setTableName(tableName);
            tableResp.setTableType("SIMPLE");
            String tComment = tableCommentMap.get(tableName.toLowerCase());
            tableResp.setTableDesc(
                    (tComment != null && !tComment.isEmpty()) ? tComment : tableName);

            List<PermissionFieldInfo> readableFields = new ArrayList<>();
            List<PermissionFieldInfo> writableFields = new ArrayList<>();
            List<PermissionFieldInfo> updatableFields = new ArrayList<>();

            List<SysModuleField> fieldsInTable =
                    sfields.stream()
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

        // B. 装配虚拟组合表
        if (!cfields.isEmpty()) {
            PermissionTableGroupResp combineTableResp = new PermissionTableGroupResp();
            combineTableResp.setTableName("virtual_combine");
            combineTableResp.setTableDesc("组合字段");
            combineTableResp.setTableType("COMBINE");

            List<PermissionFieldInfo> readableFields = new ArrayList<>();
            List<PermissionFieldInfo> writableFields = new ArrayList<>();
            List<PermissionFieldInfo> updatableFields = new ArrayList<>();

            for (SysModuleField cf : cfields) {
                PermissionFieldInfo fieldInfo =
                        PermissionFieldInfo.builder()
                                .id(cf.getId())
                                .fieldCode(cf.getFieldCode())
                                .fieldName(cf.getDisplayName())
                                .build();

                SysRoleModuleFieldPermission perm = permissionMap.get(cf.getId());
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

            combineTableResp.setReadableFields(readableFields);
            combineTableResp.setWritableFields(writableFields);
            combineTableResp.setUpdatableFields(updatableFields);
            tablesList.add(combineTableResp);
        }

        return tablesList;
    }
}
