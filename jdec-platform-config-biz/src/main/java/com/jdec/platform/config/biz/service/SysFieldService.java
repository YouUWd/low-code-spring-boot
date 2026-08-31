package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.SysFieldApi;
import com.jdec.platform.config.api.dto.request.SysFieldSaveReq;
import com.jdec.platform.config.api.dto.request.SysFieldValueSaveReq;
import com.jdec.platform.config.api.dto.response.ColumnInfoResp;
import com.jdec.platform.config.api.dto.response.SysFieldCombineInfoResp;
import com.jdec.platform.config.api.dto.response.SysFieldResp;
import com.jdec.platform.config.api.dto.response.SysFieldSourceMappingResp;
import com.jdec.platform.config.api.dto.response.SysFieldValueResp;
import com.jdec.platform.config.api.dto.response.TableInfoResp;
import com.jdec.platform.config.biz.audit.service.SysFieldLogService;
import com.jdec.platform.config.biz.check.CheckConstant;
import com.jdec.platform.config.biz.check.ReferenceCheckManager;
import com.jdec.platform.config.biz.check.ReferenceContext;
import com.jdec.platform.config.biz.entity.*;
import com.jdec.platform.config.biz.mapper.*;
import com.jdec.platform.config.biz.util.AuditLogHelper;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.exception.PopException;
import com.jdec.platform.shared.third.WordApiClient;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 系统字段配置服务
 *
 * <p>提供数据库元数据查询及字段显示配置的管理功能。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_ENGINE)
public class SysFieldService implements SysFieldApi {

    private final SysFieldMapper sysFieldMapper;
    private final SysFieldValueMapper sysFieldValueMapper;
    private final SysModuleMapper sysModuleMapper;
    private final SysModuleTableMapper sysModuleTableMapper;
    private final SysModuleFieldMapper sysModuleFieldMapper;
    private final SysApprovalChainTypeMapper sysApprovalChainTypeMapper;
    private final JdbcTemplate jdbcTemplate;
    private final WordApiClient wordApiClient;
    private final SysDataPermissionMapper sysDataPermissionMapper;
    private final ReferenceCheckManager referenceCheckManager;
    private final AuditLogHelper auditLogHelper;
    private final SysDataSnapshotService snapshotService;
    private final ObjectMapper objectMapper;
    private final SysFieldLogService sysFieldLogService;

    // ==================== 元数据查询逻辑 ====================

    @Override
    public List<TableInfoResp> getAllTables(String projectNo) {
        log.debug("查询数据源 {} 的所有表", projectNo);

        String sql =
                """
                        SELECT TABLE_NAME, TABLE_COMMENT
                        FROM INFORMATION_SCHEMA.TABLES
                        WHERE TABLE_SCHEMA = ?
                        ORDER BY TABLE_NAME
                        """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, projectNo);
            return rows.stream()
                    .map(
                            row ->
                                    TableInfoResp.builder()
                                            .tableName((String) row.get("TABLE_NAME"))
                                            .tableComment((String) row.get("TABLE_COMMENT"))
                                            .build())
                    .toList();
        } catch (Exception e) {
            log.error("查询数据源 {} 的表信息失败", projectNo, e);
            throw new RuntimeException("查询表信息失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TableInfoResp> getAllTablesWithColumns(String projectNo, Long subjectId) {
        log.debug("查询项目 {} 主体 {} 的所有表及其列信息", projectNo, subjectId);

        String sql =
                """
                        SELECT
                            t.TABLE_NAME,
                            t.TABLE_COMMENT,
                            c.COLUMN_NAME,
                            c.COLUMN_TYPE,
                            c.COLUMN_COMMENT
                        FROM INFORMATION_SCHEMA.TABLES t
                        LEFT JOIN INFORMATION_SCHEMA.COLUMNS c
                            ON t.TABLE_SCHEMA = c.TABLE_SCHEMA
                           AND t.TABLE_NAME = c.TABLE_NAME
                        WHERE t.TABLE_SCHEMA = ?
                        ORDER BY t.TABLE_NAME, c.ORDINAL_POSITION
                        """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, projectNo);

            // 查询所有 SysField 配置 Map<tableName, Map<columnName, SysField>>
            List<SysField> sysFields =
                    sysFieldMapper.selectList(
                            Wrappers.<SysField>lambdaQuery()
                                    .eq(SysField::getProjectNo, projectNo)
                                    .eq(SysField::getSubjectId, subjectId)
                                    .eq(SysField::getDeleted, 0));
            Map<String, Map<String, SysField>> sysFieldEntityMap = new HashMap<>();
            for (SysField sf : sysFields) {
                if (sf.getTableName() != null && sf.getColumnName() != null) {
                    sysFieldEntityMap
                            .computeIfAbsent(sf.getTableName(), k -> new HashMap<>())
                            .put(sf.getColumnName(), sf);
                }
            }

            Map<String, TableInfoResp> tableMap = new LinkedHashMap<>();

            for (Map<String, Object> row : rows) {
                String tableName = (String) row.get("TABLE_NAME");
                String tableComment = (String) row.get("TABLE_COMMENT");
                String colName = (String) row.get("COLUMN_NAME");

                TableInfoResp tableResp =
                        tableMap.computeIfAbsent(
                                tableName,
                                k ->
                                        TableInfoResp.builder()
                                                .tableName(tableName)
                                                .tableComment(tableComment)
                                                .columns(new ArrayList<>())
                                                .build());

                if (colName != null) {
                    String colType = (String) row.get("COLUMN_TYPE");
                    String colComment = (String) row.get("COLUMN_COMMENT");

                    Map<String, SysField> colConfigMap =
                            sysFieldEntityMap.getOrDefault(tableName, Collections.emptyMap());
                    SysField sf = colConfigMap.get(colName);

                    String displayName =
                            (sf != null && StringUtils.hasText(sf.getDisplayName()))
                                    ? sf.getDisplayName()
                                    : null;
                    if (!StringUtils.hasText(displayName)) {
                        displayName = StringUtils.hasText(colComment) ? colComment : colName;
                    }
                    Integer combineFlag =
                            (sf != null && StringUtils.hasText(sf.getCombineInfo())) ? 1 : 0;

                    ColumnInfoResp colResp =
                            ColumnInfoResp.builder()
                                    .columnName(colName)
                                    .columnType(colType)
                                    .columnComment(colComment)
                                    .displayName(displayName)
                                    .combineFlag(combineFlag)
                                    .build();

                    tableResp.getColumns().add(colResp);
                }
            }

            return new ArrayList<>(tableMap.values());
        } catch (Exception e) {
            log.error("查询项目 {} 的表及列信息失败", projectNo, e);
            throw new RuntimeException("查询表及列信息失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ColumnInfoResp> getTableColumns(
            String projectNo, Long subjectId, String tableName) {
        log.debug("查询项目 {} 主体 {} 表 {} 的列信息", projectNo, subjectId, tableName);

        String sql =
                """
                        SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT, IS_NULLABLE, COLUMN_KEY, EXTRA
                        FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                        ORDER BY ORDINAL_POSITION
                        """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, projectNo, tableName);
            List<ColumnInfoResp> columns =
                    rows.stream()
                            .map(
                                    row ->
                                            ColumnInfoResp.builder()
                                                    .columnName((String) row.get("COLUMN_NAME"))
                                                    .columnType((String) row.get("COLUMN_TYPE"))
                                                    .columnComment(
                                                            (String) row.get("COLUMN_COMMENT"))
                                                    .build())
                            .toList();

            enrichColumnsWithSysField(projectNo, subjectId, tableName, columns);

            return columns;
        } catch (Exception e) {
            log.error("查询表 {}.{} 的列信息失败", projectNo, tableName, e);
            throw new RuntimeException("查询列信息失败: " + e.getMessage(), e);
        }
    }

    private void enrichColumnsWithSysField(
            String projectNo, Long subjectId, String tableName, List<ColumnInfoResp> columns) {
        try {
            List<SysField> sysFields = querySysFieldEntities(projectNo, subjectId, tableName);
            Map<String, SysField> configMap =
                    sysFields.stream()
                            .filter(sf -> sf.getColumnName() != null)
                            .collect(
                                    Collectors.toMap(
                                            SysField::getColumnName, sf -> sf, (v1, v2) -> v1));

            columns.forEach(
                    column -> {
                        SysField sf = configMap.get(column.getColumnName());
                        String displayName =
                                (sf != null && StringUtils.hasText(sf.getDisplayName()))
                                        ? sf.getDisplayName()
                                        : null;
                        if (StringUtils.hasText(displayName)) {
                            column.setDisplayName(displayName);
                        } else {
                            column.setDisplayName(
                                    StringUtils.hasText(column.getColumnComment())
                                            ? column.getColumnComment()
                                            : column.getColumnName());
                        }
                        Integer combineFlag =
                                (sf != null && StringUtils.hasText(sf.getCombineInfo())) ? 1 : 0;
                        column.setCombineFlag(combineFlag);
                    });
        } catch (Exception e) {
            log.warn("增强表 {} 的列信息失败: {}", tableName, e.getMessage());
        }
    }

    // ==================== 字段配置持久化逻辑 ====================

    @Override
    public List<SysFieldResp> querySysFields(
            String projectNo, Long subjectId, String tableName, String columnName) {
        return querySysFieldsInternal(projectNo, subjectId, tableName, columnName, true);
    }

    @Override
    public List<SysFieldResp> querySimpleSysFields(
            String projectNo, Long subjectId, String tableName, String columnName) {
        return querySysFieldsInternal(projectNo, subjectId, tableName, columnName, false);
    }

    private List<SysFieldResp> querySysFieldsInternal(
            String projectNo,
            Long subjectId,
            String tableName,
            String columnName,
            boolean includeWordDesc) {
        log.debug(
                "查询项目 {} 主体 {} 表 {} 的字段配置列表 (includeWordDesc={})",
                projectNo,
                subjectId,
                tableName,
                includeWordDesc);

        boolean hasColumnName = columnName != null && !columnName.isBlank();

        // 1. 获取数据库中该表所有的列名、列类型和注释
        String sql =
                hasColumnName
                        ? """
                                SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
                                FROM INFORMATION_SCHEMA.COLUMNS
                                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?
                                ORDER BY ORDINAL_POSITION
                                """
                        : """
                                SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
                                FROM INFORMATION_SCHEMA.COLUMNS
                                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                                ORDER BY ORDINAL_POSITION
                                """;

        List<Map<String, Object>> columnRows =
                hasColumnName
                        ? jdbcTemplate.queryForList(sql, projectNo, tableName, columnName)
                        : jdbcTemplate.queryForList(sql, projectNo, tableName);

        // 2. 获取已有的配置信息
        List<SysField> sysFields =
                querySysFieldEntities(projectNo, subjectId, tableName, columnName);

        // 2.1 获取关联的扩展值信息
        List<Long> fieldIds = sysFields.stream().map(SysField::getId).toList();

        // 2.1.1 查询并缓存 relationBusinessNo 的词云映射（仅在 includeWordDesc 为 true 时查询）
        Map<String, Map<String, String>> wordMapsByBusinessNo = new HashMap<>();
        if (includeWordDesc) {
            for (SysField field : sysFields) {
                String relationBusinessNo = field.getRelationBusinessNo();
                if (relationBusinessNo != null && !relationBusinessNo.isBlank()) {
                    wordMapsByBusinessNo.computeIfAbsent(
                            relationBusinessNo,
                            k -> {
                                try {
                                    List<Map<String, Object>> relationWords =
                                            wordApiClient.getRelationWord(
                                                    k, new TypeReference<>() {});
                                    if (relationWords != null) {
                                        Map<String, String> wordMap = new HashMap<>();
                                        for (Map<String, Object> word : relationWords) {
                                            Object wordNo = word.get("word_no");
                                            Object wordName = word.get("word_name");
                                            if (wordNo != null) {
                                                wordMap.put(
                                                        wordNo.toString(),
                                                        wordName != null
                                                                ? wordName.toString()
                                                                : "");
                                            }
                                        }
                                        return wordMap;
                                    }
                                } catch (Exception e) {
                                    log.warn("获取关系词列表失败: businessNo={}", k, e);
                                }
                                return Collections.emptyMap();
                            });
                }
            }
        }

        Map<Long, String> fieldRelationMap =
                sysFields.stream()
                        .filter(
                                f ->
                                        f.getRelationBusinessNo() != null
                                                && !f.getRelationBusinessNo().isBlank())
                        .collect(
                                Collectors.toMap(
                                        SysField::getId,
                                        SysField::getRelationBusinessNo,
                                        (v1, v2) -> v1));

        List<SysFieldValue> dbValues =
                fieldIds.isEmpty()
                        ? Collections.emptyList()
                        : sysFieldValueMapper.selectList(
                                new LambdaQueryWrapper<SysFieldValue>()
                                        .in(SysFieldValue::getFieldId, fieldIds)
                                        .eq(SysFieldValue::getDeleted, 0));

        Set<Long> chainTypeIds =
                dbValues.stream()
                        .map(SysFieldValue::getApprovalChainTypeId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        Map<Long, String> chainTypeMap = Collections.emptyMap();
        if (!chainTypeIds.isEmpty()) {
            chainTypeMap =
                    sysApprovalChainTypeMapper
                            .selectList(
                                    Wrappers.<SysApprovalChainType>lambdaQuery()
                                            .in(SysApprovalChainType::getId, chainTypeIds))
                            .stream()
                            .collect(
                                    Collectors.toMap(
                                            SysApprovalChainType::getId,
                                            SysApprovalChainType::getTitle));
        }

        final Map<Long, String> finalChainTypeMap = chainTypeMap;

        Map<Long, List<SysFieldValueResp>> valueMap =
                dbValues.stream()
                        .map(
                                v -> {
                                    String rNo = fieldRelationMap.get(v.getFieldId());
                                    String desc = null;
                                    if (rNo != null) {
                                        Map<String, String> wordMap = wordMapsByBusinessNo.get(rNo);
                                        if (wordMap != null) {
                                            desc = wordMap.get(v.getFieldValue());
                                        }
                                    }
                                    return SysFieldValueResp.builder()
                                            .id(v.getId())
                                            .fieldId(v.getFieldId())
                                            .moduleId(v.getModuleId())
                                            .moduleName(v.getModuleName())
                                            .fieldValue(v.getFieldValue())
                                            .fieldValueDesc(desc)
                                            .approvalChainTypeId(v.getApprovalChainTypeId())
                                            .approvalChainTypeName(
                                                    finalChainTypeMap.get(
                                                            v.getApprovalChainTypeId()))
                                            .sortOrder(v.getSortOrder())
                                            .enabled(v.getEnabled())
                                            .build();
                                })
                        .collect(Collectors.groupingBy(SysFieldValueResp::getFieldId));

        Map<String, SysField> configMap =
                sysFields.stream().collect(Collectors.toMap(SysField::getColumnName, f -> f));

        // 2.2 先按 tableName 查询涉及的 field 关系，再精准匹配模块
        List<SysModuleField> simpleFields =
                sysModuleFieldMapper.selectList(
                        new LambdaQueryWrapper<SysModuleField>()
                                .eq(SysModuleField::getTableName, tableName));

        Map<String, List<SysFieldResp.SysFieldModuleResp>> columnModulesMap =
                Collections.emptyMap();
        if (!simpleFields.isEmpty()) {
            Set<Long> involvedModuleIds =
                    simpleFields.stream()
                            .map(SysModuleField::getModuleId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());

            if (!involvedModuleIds.isEmpty()) {
                List<SysModule> modules =
                        sysModuleMapper.selectList(
                                new LambdaQueryWrapper<SysModule>()
                                        .in(SysModule::getId, involvedModuleIds)
                                        .eq(SysModule::getProjectNo, projectNo)
                                        .eq(SysModule::getSubjectId, subjectId));

                Map<Long, SysModule> moduleMap =
                        modules.stream()
                                .collect(
                                        Collectors.toMap(SysModule::getId, m -> m, (m1, m2) -> m1));

                columnModulesMap =
                        simpleFields.stream()
                                .filter(sf -> moduleMap.containsKey(sf.getModuleId()))
                                .collect(
                                        Collectors.groupingBy(
                                                SysModuleField::getColumnName,
                                                Collectors.mapping(
                                                        sf -> {
                                                            SysModule m =
                                                                    moduleMap.get(sf.getModuleId());
                                                            return SysFieldResp.SysFieldModuleResp
                                                                    .builder()
                                                                    .moduleId(m.getId())
                                                                    .moduleName(m.getModuleName())
                                                                    .build();
                                                        },
                                                        Collectors.toList())));
            }
        }

        // 3. 以元数据驱动进行合并
        Map<String, List<SysFieldResp.SysFieldModuleResp>> finalColumnModulesMap = columnModulesMap;
        List<SysFieldResp> resultList =
                columnRows.stream()
                        .filter(
                                row ->
                                        columnName == null
                                                || columnName.isBlank()
                                                || columnName.equals(row.get("COLUMN_NAME")))
                        .map(
                                row -> {
                                    String colName = (String) row.get("COLUMN_NAME");
                                    String columnComment = (String) row.get("COLUMN_COMMENT");
                                    String columnType = (String) row.get("COLUMN_TYPE");
                                    SysField config = configMap.get(colName);

                                    if (config != null) {
                                        // 已有配置
                                        return SysFieldResp.builder()
                                                .id(config.getId())
                                                .projectNo(projectNo)
                                                .subjectId(subjectId)
                                                .tableName(tableName)
                                                .columnName(colName)
                                                .columnType(columnType)
                                                .displayName(config.getDisplayName())
                                                .relationBusinessNo(config.getRelationBusinessNo())
                                                .relationName(config.getRelationName())
                                                .encrypted(config.getEncrypted())
                                                .dataRightFlag(config.getDataRightFlag())
                                                .combineInfo(
                                                        parseCombineInfoDTO(
                                                                config.getCombineInfo()))
                                                .fieldValues(
                                                        valueMap.getOrDefault(
                                                                config.getId(),
                                                                Collections.emptyList()))
                                                .modules(
                                                        finalColumnModulesMap.getOrDefault(
                                                                colName, Collections.emptyList()))
                                                .build();
                                    } else {
                                        // 尚无配置，返回默认值
                                        return SysFieldResp.builder()
                                                .projectNo(projectNo)
                                                .subjectId(subjectId)
                                                .tableName(tableName)
                                                .columnName(colName)
                                                .columnType(columnType)
                                                .displayName(
                                                        columnComment != null
                                                                        && !columnComment.isEmpty()
                                                                ? columnComment
                                                                : colName)
                                                .encrypted(0)
                                                .encrypted(0)
                                                .fieldValues(Collections.emptyList())
                                                .modules(
                                                        finalColumnModulesMap.getOrDefault(
                                                                colName, Collections.emptyList()))
                                                .build();
                                    }
                                })
                        .toList();

        List<SysFieldCombineInfoResp> combineInfos =
                resultList.stream()
                        .map(SysFieldResp::getCombineInfo)
                        .filter(Objects::nonNull)
                        .toList();
        enrichCombineInfoDisplayNames(projectNo, subjectId, combineInfos);

        return resultList;
    }

    public List<SysField> querySysFieldEntities(
            String projectNo, Long subjectId, String tableName) {
        return querySysFieldEntities(projectNo, subjectId, tableName, null);
    }

    public List<SysField> querySysFieldEntities(
            String projectNo, Long subjectId, String tableName, String columnName) {
        LambdaQueryWrapper<SysField> wrapper =
                new LambdaQueryWrapper<SysField>()
                        .eq(SysField::getProjectNo, projectNo)
                        .eq(SysField::getSubjectId, subjectId)
                        .eq(SysField::getTableName, tableName)
                        .eq(SysField::getDeleted, 0);
        if (columnName != null && !columnName.isBlank()) {
            wrapper.eq(SysField::getColumnName, columnName);
        }
        return sysFieldMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public SysFieldResp saveSysField(
            String projectNo, Long subjectId, String tableName, SysFieldSaveReq req) {
        log.info(
                "开始保存项目 {} 主体 {} 的字段配置: {}/{}",
                projectNo,
                subjectId,
                tableName,
                req.getColumnName());

        // 更新时捕获修改前的字段配置及字段值，用于审计日志差异对比
        SysField oldField = null;
        List<SysFieldValue> oldFieldValues = Collections.emptyList();
        if (req.getId() != null) {
            oldField = sysFieldMapper.selectById(req.getId());
            if (oldField != null) {
                oldFieldValues =
                        sysFieldValueMapper.selectList(
                                new LambdaQueryWrapper<SysFieldValue>()
                                        .eq(SysFieldValue::getFieldId, oldField.getId())
                                        .eq(SysFieldValue::getDeleted, 0));
            }
        }
        String oldBusinessNo = oldField != null ? oldField.getRelationBusinessNo() : null;
        Integer oldDataRightFlag = oldField != null ? oldField.getDataRightFlag() : null;

        String combineInfoJson = null;
        if (req.getCombineInfo() != null) {
            try {
                combineInfoJson = objectMapper.writeValueAsString(req.getCombineInfo());
            } catch (Exception e) {
                log.error("combineInfo 序列化失败", e);
            }
        } else {
            combineInfoJson = null;
        }

        SysField entity =
                SysField.builder()
                        .id(req.getId())
                        .projectNo(projectNo)
                        .subjectId(subjectId)
                        .tableName(tableName)
                        .columnName(req.getColumnName())
                        .displayName(req.getDisplayName())
                        .relationBusinessNo(req.getRelationBusinessNo())
                        .relationName(req.getRelationName())
                        .encrypted(req.getEncrypted())
                        .dataRightFlag(req.getDataRightFlag())
                        .combineInfo(combineInfoJson)
                        .deleted(0)
                        .build();

        if (entity.getId() == null) {
            sysFieldMapper.insert(entity);
        } else {
            sysFieldMapper.updateById(entity);
        }

        // 只在 dataRightFlag 发生变化时处理权限节点
        Integer newDataRightFlag = req.getDataRightFlag();
        boolean dataRightFlagChanged =
                oldDataRightFlag == null || !oldDataRightFlag.equals(newDataRightFlag);

        if (dataRightFlagChanged) {
            // 如果旧值为 1 或权限节点存在，需要删除
            if (Integer.valueOf(1).equals(oldDataRightFlag)) {
                SysDataPermission dataPermission =
                        sysDataPermissionMapper.selectOne(
                                new LambdaQueryWrapper<SysDataPermission>()
                                        .eq(
                                                SysDataPermission::getCode,
                                                entity.getTableName()
                                                        + "*"
                                                        + entity.getColumnName())
                                        .eq(
                                                SysDataPermission::getSubjectId,
                                                AppContext.getSubjectId())
                                        .eq(
                                                SysDataPermission::getProjectNo,
                                                AppContext.getProjectNo()));
                if (dataPermission != null) {
                    // 数据权限节点引用检查
                    ReferenceContext context =
                            ReferenceContext.builder()
                                    .targetType(CheckConstant.SYS_DATA_PERMISSION)
                                    .targetId(dataPermission.getId())
                                    .targetName(dataPermission.getName())
                                    .build();
                    List<String> check = referenceCheckManager.check(context);
                    if (!check.isEmpty()) {
                        throw new PopException(String.join("\n", check));
                    }

                    // 删除前确保快照存在（用于审计日志）
                    SysDataSnapshot existingSnapshot =
                            snapshotService.getLatestSnapshot(
                                    "sys_data_permission", dataPermission.getId());
                    if (existingSnapshot == null) {
                        try {
                            String snapshotJson = objectMapper.writeValueAsString(dataPermission);
                            snapshotService.saveSnapshot(
                                    "sys_data_permission", dataPermission.getId(), snapshotJson);
                        } catch (Exception e) {
                            log.warn("删除数据权限节点前创建快照失败: id={}", dataPermission.getId(), e);
                        }
                    }

                    // 执行删除操作
                    sysDataPermissionMapper.delete(
                            new LambdaQueryWrapper<SysDataPermission>()
                                    .eq(
                                            SysDataPermission::getCode,
                                            entity.getTableName() + "*" + entity.getColumnName())
                                    .eq(SysDataPermission::getSubjectId, AppContext.getSubjectId())
                                    .eq(
                                            SysDataPermission::getProjectNo,
                                            AppContext.getProjectNo()));

                    // 记录删除数据权限节点的审计日志（会自动删除快照）
                    auditLogHelper.logDelete(
                            "数据权限",
                            "sys_data_permission",
                            dataPermission.getId(),
                            SysDataPermission.class);
                }
            }

            // 如果新值为 1，创建权限节点
            if (Integer.valueOf(1).equals(newDataRightFlag)) {
                SysDataPermission permission = new SysDataPermission();
                permission.setCode(entity.getTableName() + "*" + entity.getColumnName());
                permission.setName(entity.getDisplayName());
                permission.setStatus(1);
                permission.setSubjectId(AppContext.getSubjectId());
                permission.setProjectNo(projectNo);
                sysDataPermissionMapper.insert(permission);

                // 记录新增数据权限节点的审计日志
                auditLogHelper.logCreate(
                        "数据权限", "sys_data_permission", "新增", permission.getId(), permission);
            }
        }
        Long fieldId = entity.getId();
        processFieldValues(fieldId, req.getFieldValues());

        // 通知 Word 系统字段绑定关系变更
        if (req.getRelationBusinessNo() != null && !req.getRelationBusinessNo().isBlank()) {
            saveToWordSystem(oldBusinessNo, req.getRelationBusinessNo(), tableName, subjectId);
        }

        // 记录字段配置修改审计日志（仅更新操作，子表按主键 id 对比 i/u/d）
        sysFieldLogService.logUpdate(oldField, tableName, req, oldFieldValues);

        // 查询并返回保存后的完整数据
        return getSysFieldById(fieldId, projectNo, subjectId, tableName);
    }

    /** 根据字段ID查询完整的字段配置 */
    private SysFieldResp getSysFieldById(
            Long fieldId, String projectNo, Long subjectId, String tableName) {
        SysField field = sysFieldMapper.selectById(fieldId);
        if (field == null) {
            return null;
        }

        String rNo = field.getRelationBusinessNo();
        Map<String, String> wordMap = null;
        if (rNo != null && !rNo.isBlank()) {
            try {
                List<Map<String, Object>> relationWords =
                        wordApiClient.getRelationWord(rNo, new TypeReference<>() {});
                if (relationWords != null) {
                    wordMap = new HashMap<>();
                    for (Map<String, Object> word : relationWords) {
                        Object wordNo = word.get("word_no");
                        Object wordName = word.get("word_name");
                        if (wordNo != null) {
                            wordMap.put(
                                    wordNo.toString(), wordName != null ? wordName.toString() : "");
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("获取关系词列表失败: businessNo={}", rNo, e);
            }
        }

        final Map<String, String> finalWordMap = wordMap;

        List<SysFieldValue> dbValues =
                sysFieldValueMapper.selectList(
                        new LambdaQueryWrapper<SysFieldValue>()
                                .eq(SysFieldValue::getFieldId, fieldId)
                                .eq(SysFieldValue::getDeleted, 0));

        Set<Long> chainTypeIds =
                dbValues.stream()
                        .map(SysFieldValue::getApprovalChainTypeId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        Map<Long, String> chainTypeMap = Collections.emptyMap();
        if (!chainTypeIds.isEmpty()) {
            chainTypeMap =
                    sysApprovalChainTypeMapper
                            .selectList(
                                    Wrappers.<SysApprovalChainType>lambdaQuery()
                                            .in(SysApprovalChainType::getId, chainTypeIds))
                            .stream()
                            .collect(
                                    Collectors.toMap(
                                            SysApprovalChainType::getId,
                                            SysApprovalChainType::getTitle));
        }

        final Map<Long, String> finalChainTypeMap = chainTypeMap;

        // 查询关联的扩展值信息
        List<SysFieldValueResp> fieldValues =
                dbValues.stream()
                        .map(
                                v -> {
                                    String desc = null;
                                    if (finalWordMap != null) {
                                        desc = finalWordMap.get(v.getFieldValue());
                                    }
                                    return SysFieldValueResp.builder()
                                            .id(v.getId())
                                            .fieldId(v.getFieldId())
                                            .moduleId(v.getModuleId())
                                            .moduleName(v.getModuleName())
                                            .fieldValue(v.getFieldValue())
                                            .fieldValueDesc(desc)
                                            .approvalChainTypeId(v.getApprovalChainTypeId())
                                            .approvalChainTypeName(
                                                    finalChainTypeMap.get(
                                                            v.getApprovalChainTypeId()))
                                            .sortOrder(v.getSortOrder())
                                            .enabled(v.getEnabled())
                                            .build();
                                })
                        .toList();

        String columnType = null;
        try {
            String typeSql =
                    """
                            SELECT COLUMN_TYPE
                            FROM INFORMATION_SCHEMA.COLUMNS
                            WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?
                            """;
            columnType =
                    jdbcTemplate.queryForObject(
                            typeSql, String.class, projectNo, tableName, field.getColumnName());
        } catch (Exception e) {
            log.warn(
                    "获取列类型失败: projectNo={}, tableName={}, columnName={}",
                    projectNo,
                    tableName,
                    field.getColumnName(),
                    e);
        }

        SysFieldResp resp =
                SysFieldResp.builder()
                        .id(field.getId())
                        .projectNo(projectNo)
                        .subjectId(subjectId)
                        .tableName(tableName)
                        .columnName(field.getColumnName())
                        .columnType(columnType)
                        .displayName(field.getDisplayName())
                        .relationBusinessNo(field.getRelationBusinessNo())
                        .relationName(field.getRelationName())
                        .encrypted(field.getEncrypted())
                        .dataRightFlag(field.getDataRightFlag())
                        .combineInfo(parseCombineInfoDTO(field.getCombineInfo()))
                        .fieldValues(fieldValues)
                        .build();

        if (resp.getCombineInfo() != null) {
            enrichCombineInfoDisplayNames(projectNo, subjectId, List.of(resp.getCombineInfo()));
        }

        return resp;
    }

    private void enrichCombineInfoDisplayNames(
            String projectNo, Long subjectId, List<SysFieldCombineInfoResp> combineInfos) {
        if (combineInfos == null || combineInfos.isEmpty()) {
            return;
        }

        Set<String> tables = new HashSet<>();
        List<SysFieldSourceMappingResp> allMappings = new ArrayList<>();
        for (SysFieldCombineInfoResp info : combineInfos) {
            if (info != null && info.getSourceMapping() != null) {
                for (SysFieldSourceMappingResp mapping : info.getSourceMapping()) {
                    if (mapping != null && StringUtils.hasText(mapping.getTable())) {
                        tables.add(mapping.getTable());
                        allMappings.add(mapping);
                    }
                }
            }
        }

        if (allMappings.isEmpty()) {
            return;
        }

        // 1. 查询 INFORMATION_SCHEMA.TABLES 获取表注释
        Map<String, String> tableDisplayNameMap = new HashMap<>();
        try {
            String inSql = String.join(",", Collections.nCopies(tables.size(), "?"));
            String tableSql =
                    "SELECT TABLE_NAME, TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME IN ("
                            + inSql
                            + ")";
            List<Object> params = new ArrayList<>();
            params.add(projectNo);
            params.addAll(tables);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(tableSql, params.toArray());
            for (Map<String, Object> row : rows) {
                String tableName = (String) row.get("TABLE_NAME");
                String comment = (String) row.get("TABLE_COMMENT");
                if (tableName != null) {
                    tableDisplayNameMap.put(
                            tableName, StringUtils.hasText(comment) ? comment : tableName);
                }
            }
        } catch (Exception e) {
            log.warn("查询表注释信息失败: projectNo={}", projectNo, e);
        }

        // 2. 查询 sys_field 获取特定 (tableName, columnName) 的自定义 displayName
        Map<String, String> sysFieldDisplayNameMap = new HashMap<>();
        try {
            List<SysField> sysFields =
                    sysFieldMapper.selectList(
                            Wrappers.<SysField>lambdaQuery()
                                    .eq(SysField::getProjectNo, projectNo)
                                    .eq(SysField::getSubjectId, subjectId)
                                    .in(SysField::getTableName, tables)
                                    .eq(SysField::getDeleted, 0));
            for (SysField sf : sysFields) {
                if (StringUtils.hasText(sf.getDisplayName())) {
                    sysFieldDisplayNameMap.put(
                            sf.getTableName() + ":" + sf.getColumnName(), sf.getDisplayName());
                }
            }
        } catch (Exception e) {
            log.warn("查询 SysField 配置的 displayName 失败: projectNo={}", projectNo, e);
        }

        // 3. 查询 INFORMATION_SCHEMA.COLUMNS 获取列注释
        Map<String, String> columnCommentMap = new HashMap<>();
        try {
            String inSql = String.join(",", Collections.nCopies(tables.size(), "?"));
            String colSql =
                    "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_COMMENT FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME IN ("
                            + inSql
                            + ")";
            List<Object> params = new ArrayList<>();
            params.add(projectNo);
            params.addAll(tables);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(colSql, params.toArray());
            for (Map<String, Object> row : rows) {
                String tableName = (String) row.get("TABLE_NAME");
                String colName = (String) row.get("COLUMN_NAME");
                String comment = (String) row.get("COLUMN_COMMENT");
                if (tableName != null && colName != null) {
                    columnCommentMap.put(
                            tableName + ":" + colName,
                            StringUtils.hasText(comment) ? comment : colName);
                }
            }
        } catch (Exception e) {
            log.warn("查询列注释失败: projectNo={}", projectNo, e);
        }

        // 4. 为每个 mapping 填充 tableDisplayName 与 fieldDisplayName
        for (SysFieldSourceMappingResp mapping : allMappings) {
            String table = mapping.getTable();
            String field = mapping.getField();
            mapping.setTableDisplayName(tableDisplayNameMap.getOrDefault(table, table));

            if (field != null) {
                String key = table + ":" + field;
                String fieldDisp = sysFieldDisplayNameMap.get(key);
                if (!StringUtils.hasText(fieldDisp)) {
                    fieldDisp = columnCommentMap.getOrDefault(key, field);
                }
                mapping.setFieldDisplayName(fieldDisp);
            }
        }
    }

    private SysFieldCombineInfoResp parseCombineInfoDTO(String combineInfoJson) {
        if (combineInfoJson == null || combineInfoJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(combineInfoJson, SysFieldCombineInfoResp.class);
        } catch (Exception e) {
            log.error("combineInfo 反序列化失败: {}", combineInfoJson, e);
            return null;
        }
    }

    /** 将字段绑定关系同步到 Word 系统 */
    private void saveToWordSystem(
            String oldBusinessNo, String businessNo, String tableName, Long subjectId) {
        try {
            HashMap<String, Object> params = new HashMap<>();
            params.put("old_business_no", oldBusinessNo);
            params.put("business_no", businessNo);

            SysModule sysModule = null;
            List<SysModuleTable> primaryTables =
                    sysModuleTableMapper.selectList(
                            Wrappers.<SysModuleTable>lambdaQuery()
                                    .eq(SysModuleTable::getTableName, tableName)
                                    .eq(SysModuleTable::getIsPrimary, 1));
            if (!primaryTables.isEmpty()) {
                List<Long> moduleIds =
                        primaryTables.stream().map(SysModuleTable::getModuleId).toList();
                sysModule =
                        sysModuleMapper
                                .selectList(
                                        Wrappers.<SysModule>lambdaQuery()
                                                .eq(SysModule::getSubjectId, subjectId)
                                                .in(SysModule::getId, moduleIds))
                                .stream()
                                .findFirst()
                                .orElse(null);
            }

            if (sysModule != null) {
                params.put("module_slug", sysModule.getModuleCode());
                params.put("module_name", sysModule.getModuleName());
            }

            params.put("type", 2);

            wordApiClient.getBindOrCancel(params);
            log.info("已同步字段绑定关系到 Word 系统: businessNo={}", businessNo);
        } catch (Exception e) {
            log.error("同步字段绑定关系到 Word 系统失败: businessNo={}", businessNo, e);
        }
    }

    /** 处理字段扩展值的级联保存 */
    private void processFieldValues(Long fieldId, List<SysFieldValueSaveReq> valueReqs) {
        // 仅根据 fieldId 清理旧配置
        sysFieldValueMapper.delete(
                new LambdaQueryWrapper<SysFieldValue>().eq(SysFieldValue::getFieldId, fieldId));

        if (valueReqs == null || valueReqs.isEmpty()) {
            return;
        }

        for (SysFieldValueSaveReq vReq : valueReqs) {
            SysFieldValue vEntity =
                    SysFieldValue.builder()
                            .fieldId(fieldId)
                            .moduleId(vReq.getModuleId())
                            .moduleName(vReq.getModuleName())
                            .fieldValue(vReq.getFieldValue())
                            .approvalChainTypeId(vReq.getApprovalChainTypeId())
                            .sortOrder(vReq.getSortOrder() != null ? vReq.getSortOrder() : 0)
                            .enabled(vReq.getEnabled() != null ? vReq.getEnabled() : 1)
                            .deleted(0)
                            .build();

            sysFieldValueMapper.insert(vEntity);
        }
        log.debug("已完成字段 {} 的 {} 条扩展配置保存", fieldId, valueReqs.size());
    }

    @Override
    public List<SysFieldResp> getDataPermissionFields(String projectNo, Long subjectId) {
        log.debug("查询项目 {} 主体 {} 的数据权限字段列表", projectNo, subjectId);

        // 查询 dataRightFlag=1 的字段
        List<SysField> fields =
                sysFieldMapper.selectList(
                        new LambdaQueryWrapper<SysField>()
                                .eq(SysField::getProjectNo, projectNo)
                                .eq(SysField::getSubjectId, subjectId)
                                .eq(SysField::getDataRightFlag, 1)
                                .eq(SysField::getDeleted, 0));

        if (fields.isEmpty()) {
            return Collections.emptyList();
        }

        // 获取所有字段对应的列类型
        Map<String, String> columnTypeMap = new HashMap<>();
        for (SysField field : fields) {
            try {
                String typeSql =
                        """
                                SELECT COLUMN_TYPE
                                FROM INFORMATION_SCHEMA.COLUMNS
                                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?
                                """;
                String columnType =
                        jdbcTemplate.queryForObject(
                                typeSql,
                                String.class,
                                projectNo,
                                field.getTableName(),
                                field.getColumnName());
                if (columnType != null) {
                    columnTypeMap.put(
                            field.getTableName() + "." + field.getColumnName(), columnType);
                }
            } catch (Exception e) {
                log.warn(
                        "获取列类型失败: tableName={}, columnName={}",
                        field.getTableName(),
                        field.getColumnName());
            }
        }

        // 转换为响应对象
        return fields.stream()
                .map(
                        field -> {
                            String columnType =
                                    columnTypeMap.get(
                                            field.getTableName() + "." + field.getColumnName());
                            return SysFieldResp.builder()
                                    .id(field.getId())
                                    .projectNo(field.getProjectNo())
                                    .subjectId(field.getSubjectId())
                                    .tableName(field.getTableName())
                                    .columnName(field.getColumnName())
                                    .columnType(columnType)
                                    .displayName(field.getDisplayName())
                                    .relationBusinessNo(field.getRelationBusinessNo())
                                    .relationName(field.getRelationName())
                                    .encrypted(field.getEncrypted())
                                    .dataRightFlag(field.getDataRightFlag())
                                    .fieldValues(Collections.emptyList())
                                    .modules(Collections.emptyList())
                                    .build();
                        })
                .toList();
    }
}
