package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysModuleFieldPermissionApi;
import com.jdec.platform.config.api.SysSchemaApi;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModuleField;
import com.jdec.platform.config.biz.entity.SysRoleModuleFieldPermission;
import com.jdec.platform.config.biz.mapper.SysModuleFieldMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.mapper.SysRoleModuleFieldPermissionMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 模块字段管理业务实现类 负责高性能模块、表与字段纯净物理树及权限的获取 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysModuleFieldPermissionService implements SysModuleFieldPermissionApi {

    private final SysModuleMapper sysModuleMapper;
    private final SysModuleFieldMapper sysModuleFieldMapper;
    private final SysRoleModuleFieldPermissionMapper sysRoleModuleFieldPermissionMapper;
    private final SysSchemaApi sysSchemaApi;

    @Override
    public List<SysModuleFieldTreeResp> getModuleTableFieldTree(
            String projectNo, Long subjectId, Integer category) {
        log.info(
                "获取纯净模块表字段树: projectNo={}, subjectId={}, category={}",
                projectNo,
                subjectId,
                category);

        // 1. 查询当前项目+主体下的所有模块
        LambdaQueryWrapper<SysModule> moduleWrapper = new LambdaQueryWrapper<>();
        moduleWrapper
                .eq(SysModule::getProjectNo, projectNo)
                .eq(SysModule::getSubjectId, subjectId)
                .eq(category != null, SysModule::getCategory, category)
                .orderByAsc(SysModule::getParentId)
                .orderByAsc(SysModule::getSortOrder);
        List<SysModule> modules = sysModuleMapper.selectList(moduleWrapper);
        if (modules.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 批量拉取所有模块关联的字段
        List<Long> moduleIds = modules.stream().map(SysModule::getId).toList();
        LambdaQueryWrapper<SysModuleField> fieldWrapper = new LambdaQueryWrapper<>();
        fieldWrapper
                .in(SysModuleField::getModuleId, moduleIds)
                .eq(SysModuleField::getStatus, 1)
                .orderByAsc(SysModuleField::getSortOrder);
        List<SysModuleField> allFields = sysModuleFieldMapper.selectList(fieldWrapper);

        // 收集所有表名（包括 primaryTable 以及所有字段归属表），批量获取真实表注释
        Set<String> allTableNames = new HashSet<>();
        for (SysModule m : modules) {
            if (StringUtils.hasText(m.getPrimaryTable())) {
                allTableNames.add(m.getPrimaryTable().trim());
            }
        }
        for (SysModuleField f : allFields) {
            if (StringUtils.hasText(f.getTableName())) {
                allTableNames.add(f.getTableName().trim());
            }
        }
        Map<String, String> tableCommentMap =
                sysSchemaApi.getTableComments(projectNo, allTableNames);

        // 按 moduleId -> (tableName -> List<SysModuleField>) 建立索引
        Map<Long, Map<String, List<SysModuleField>>> fieldsByModuleTable =
                allFields.stream()
                        .filter(f -> StringUtils.hasText(f.getTableName()))
                        .collect(
                                Collectors.groupingBy(
                                        SysModuleField::getModuleId,
                                        Collectors.groupingBy(
                                                SysModuleField::getTableName,
                                                LinkedHashMap::new,
                                                Collectors.toList())));

        // 3. 构建第一层与第二层、第三层：模块 -> 表 -> 字段
        List<SysModuleFieldTreeResp> dtoList =
                modules.stream()
                        .map(
                                m ->
                                        buildModuleNode(
                                                m,
                                                fieldsByModuleTable.getOrDefault(
                                                        m.getId(), Collections.emptyMap()),
                                                tableCommentMap))
                        .toList();

        // 4. 双亲委派挂载子模块 children 树形结构
        return assembleModuleTree(dtoList, modules);
    }

    /** 构建单个模块节点（装配下属表及字段列表） */
    private SysModuleFieldTreeResp buildModuleNode(
            SysModule module,
            Map<String, List<SysModuleField>> tableFieldsMap,
            Map<String, String> tableCommentMap) {
        SysModuleFieldTreeResp dto = new SysModuleFieldTreeResp();
        dto.setModuleId(module.getId());
        dto.setModuleName(module.getModuleName());
        dto.setModuleCode(module.getModuleCode());
        dto.setChildren(new ArrayList<>());

        List<ModuleTableTreeResp> tableNodes = new ArrayList<>();
        Set<String> processedTables = new LinkedHashSet<>();

        // 若配置了主表，且主表有字段或需要置顶呈现
        String primaryTable = module.getPrimaryTable();
        if (StringUtils.hasText(primaryTable)) {
            String trimmedPrimary = primaryTable.trim();
            for (String tableName : tableFieldsMap.keySet()) {
                if (trimmedPrimary.equalsIgnoreCase(tableName)) {
                    processedTables.add(tableName);
                    String tableDesc =
                            tableCommentMap.getOrDefault(tableName.toLowerCase(), tableName);
                    tableNodes.add(
                            buildTableNode(tableName, tableDesc, tableFieldsMap.get(tableName)));
                    break;
                }
            }
        }

        // 遍历其余表
        for (Map.Entry<String, List<SysModuleField>> entry : tableFieldsMap.entrySet()) {
            String tableName = entry.getKey();
            if (!processedTables.contains(tableName)) {
                String tableDesc = tableCommentMap.getOrDefault(tableName.toLowerCase(), tableName);
                tableNodes.add(buildTableNode(tableName, tableDesc, entry.getValue()));
            }
        }

        dto.setTables(tableNodes);
        return dto;
    }

    /** 构建表节点及内部字段列表 */
    private ModuleTableTreeResp buildTableNode(
            String tableName, String tableDesc, List<SysModuleField> fields) {
        ModuleTableTreeResp tableResp = new ModuleTableTreeResp();
        tableResp.setTableName(tableName);
        tableResp.setTableDesc(tableDesc);

        List<ModuleFieldTreeResp> fieldNodes = new ArrayList<>();
        if (fields != null) {
            for (SysModuleField f : fields) {
                ModuleFieldTreeResp fieldNode = new ModuleFieldTreeResp();
                fieldNode.setId(f.getId());
                fieldNode.setModuleId(f.getModuleId());
                fieldNode.setTableName(f.getTableName());
                fieldNode.setColumnName(f.getColumnName());
                fieldNode.setDisplayName(
                        StringUtils.hasText(f.getDisplayName())
                                ? f.getDisplayName()
                                : f.getColumnName());
                fieldNodes.add(fieldNode);
            }
        }
        tableResp.setFields(fieldNodes);
        return tableResp;
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

    @Override
    public List<PermissionTableGroupResp> getRoleModuleFieldPermissions(
            String projectNo, Long subjectId, Long roleId, Long moduleId) {
        log.info(
                "查询角色字段权限表级分组集合: projectNo={}, subjectId={}, roleId={}, moduleId={}",
                projectNo,
                subjectId,
                roleId,
                moduleId);

        // 1. 获取并校验模块信息
        SysModule module = sysModuleMapper.selectById(moduleId);
        if (module == null
                || !Objects.equals(module.getProjectNo(), projectNo)
                || !Objects.equals(module.getSubjectId(), subjectId)) {
            throw new BusinessException(404, "模块不存在");
        }

        // 2. 查询该模块下的所有字段（按 sortOrder 升序），并在内存中按表归类
        List<SysModuleField> allFields =
                sysModuleFieldMapper.selectList(
                        Wrappers.<SysModuleField>lambdaQuery()
                                .eq(SysModuleField::getModuleId, moduleId)
                                .eq(SysModuleField::getStatus, 1)
                                .orderByAsc(SysModuleField::getSortOrder));

        Map<String, List<SysModuleField>> fieldsByTable =
                allFields.stream()
                        .filter(f -> StringUtils.hasText(f.getTableName()))
                        .collect(
                                Collectors.groupingBy(
                                        SysModuleField::getTableName,
                                        LinkedHashMap::new,
                                        Collectors.toList()));

        // 3. 查询该角色模块下的字段权限记录，建立 fieldId -> Entity 映射 Map
        List<SysRoleModuleFieldPermission> permissions =
                sysRoleModuleFieldPermissionMapper.selectList(
                        Wrappers.<SysRoleModuleFieldPermission>lambdaQuery()
                                .eq(SysRoleModuleFieldPermission::getRoleId, roleId)
                                .eq(SysRoleModuleFieldPermission::getModuleId, moduleId));
        Map<Long, SysRoleModuleFieldPermission> permissionMap =
                permissions.stream()
                        .collect(
                                Collectors.toMap(
                                        SysRoleModuleFieldPermission::getModuleFieldId,
                                        p -> p,
                                        (p1, p2) -> p1));

        // 4. 按表维度装配权限列表，确保主表 (primaryTable) 在首位
        List<PermissionTableGroupResp> tablesList = new ArrayList<>();
        Set<String> processedTables = new LinkedHashSet<>();

        String primaryTable = module.getPrimaryTable();
        if (StringUtils.hasText(primaryTable)) {
            String trimmedPrimary = primaryTable.trim();
            for (String tableName : fieldsByTable.keySet()) {
                if (trimmedPrimary.equalsIgnoreCase(tableName)) {
                    processedTables.add(tableName);
                    tablesList.add(
                            buildPermissionTableGroup(
                                    tableName, fieldsByTable.get(tableName), permissionMap));
                    break;
                }
            }
        }

        for (Map.Entry<String, List<SysModuleField>> entry : fieldsByTable.entrySet()) {
            String tableName = entry.getKey();
            if (!processedTables.contains(tableName)) {
                tablesList.add(
                        buildPermissionTableGroup(tableName, entry.getValue(), permissionMap));
            }
        }

        return tablesList;
    }

    /** 构建单表的角色权限分组信息 */
    private PermissionTableGroupResp buildPermissionTableGroup(
            String tableName,
            List<SysModuleField> fieldsInTable,
            Map<Long, SysRoleModuleFieldPermission> permissionMap) {
        PermissionTableGroupResp tableResp = new PermissionTableGroupResp();
        tableResp.setTableName(tableName);

        List<PermissionFieldInfo> readableFields = new ArrayList<>();
        List<PermissionFieldInfo> writableFields = new ArrayList<>();
        List<PermissionFieldInfo> updatableFields = new ArrayList<>();

        if (fieldsInTable != null) {
            for (SysModuleField f : fieldsInTable) {
                String displayName =
                        StringUtils.hasText(f.getDisplayName())
                                ? f.getDisplayName()
                                : f.getColumnName();

                PermissionFieldInfo fieldInfo =
                        PermissionFieldInfo.builder()
                                .id(f.getId())
                                .moduleId(f.getModuleId())
                                .tableName(f.getTableName())
                                .columnName(f.getColumnName())
                                .displayName(displayName)
                                .build();

                SysRoleModuleFieldPermission perm = permissionMap.get(f.getId());
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
        }

        tableResp.setReadableFields(readableFields);
        tableResp.setWritableFields(writableFields);
        tableResp.setUpdatableFields(updatableFields);
        return tableResp;
    }
}
