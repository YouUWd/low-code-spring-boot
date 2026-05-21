package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.api.SysFieldApi;
import com.jdec.platform.config.api.dto.request.SysFieldSaveReq;
import com.jdec.platform.config.api.dto.request.SysFieldValueSaveReq;
import com.jdec.platform.config.api.dto.response.ColumnInfoResp;
import com.jdec.platform.config.api.dto.response.SysFieldResp;
import com.jdec.platform.config.api.dto.response.SysFieldValueResp;
import com.jdec.platform.config.api.dto.response.TableInfoResp;
import com.jdec.platform.config.biz.entity.SysField;
import com.jdec.platform.config.biz.entity.SysFieldValue;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.entity.SysModuleSimpleField;
import com.jdec.platform.config.biz.mapper.SysFieldMapper;
import com.jdec.platform.config.biz.mapper.SysFieldValueMapper;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.config.biz.mapper.SysModuleSimpleFieldMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.third.WordApiClient;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统字段配置服务
 *
 * <p>提供数据库元数据查询及字段显示配置的管理功能。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.CONFIG_CENTER)
public class SysFieldService implements SysFieldApi {

    private final SysFieldMapper sysFieldMapper;
    private final SysFieldValueMapper sysFieldValueMapper;
    private final SysModuleMapper sysModuleMapper;
    private final SysModuleSimpleFieldMapper sysModuleSimpleFieldMapper;
    private final JdbcTemplate jdbcTemplate;
    private final WordApiClient wordApiClient;

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
            Map<String, String> configMap =
                    sysFields.stream()
                            .collect(
                                    Collectors.toMap(
                                            SysField::getColumnName, SysField::getDisplayName));

            columns.forEach(
                    column -> {
                        String displayName = configMap.get(column.getColumnName());
                        if (displayName != null) {
                            column.setDisplayName(displayName);
                        } else {
                            column.setDisplayName(
                                    column.getColumnComment() != null
                                            ? column.getColumnComment()
                                            : column.getColumnName());
                        }
                    });
        } catch (Exception e) {
            log.warn("增强表 {} 的列信息失败: {}", tableName, e.getMessage());
        }
    }

    // ==================== 字段配置持久化逻辑 ====================

    @Override
    public List<SysFieldResp> querySysFields(
            String projectNo, Long subjectId, String tableName, String columnName) {
        log.debug("查询项目 {} 主体 {} 表 {} 的完整字段配置列表", projectNo, subjectId, tableName);

        // 1. 获取数据库中该表所有的列名和注释
        String sql =
                """
            SELECT COLUMN_NAME, COLUMN_COMMENT
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
            ORDER BY ORDINAL_POSITION
            """;

        List<Map<String, Object>> columnRows = jdbcTemplate.queryForList(sql, projectNo, tableName);

        // 2. 获取已有的配置信息
        List<SysField> sysFields = querySysFieldEntities(projectNo, subjectId, tableName);

        // 2.1 获取关联的扩展值信息
        List<Long> fieldIds = sysFields.stream().map(SysField::getId).toList();
        Map<Long, List<SysFieldValueResp>> valueMap =
                fieldIds.isEmpty()
                        ? Collections.emptyMap()
                        : sysFieldValueMapper
                                .selectList(
                                        new LambdaQueryWrapper<SysFieldValue>()
                                                .in(SysFieldValue::getFieldId, fieldIds)
                                                .eq(SysFieldValue::getDeleted, 0))
                                .stream()
                                .map(
                                        v ->
                                                SysFieldValueResp.builder()
                                                        .id(v.getId())
                                                        .fieldId(v.getFieldId())
                                                        .moduleId(v.getModuleId())
                                                        .moduleName(v.getModuleName())
                                                        .fieldValue(v.getFieldValue())
                                                        .approvalChainTypeId(
                                                                v.getApprovalChainTypeId())
                                                        .sortOrder(v.getSortOrder())
                                                        .enabled(v.getEnabled())
                                                        .build())
                                .collect(Collectors.groupingBy(SysFieldValueResp::getFieldId));

        Map<String, SysField> configMap =
                sysFields.stream().collect(Collectors.toMap(SysField::getColumnName, f -> f));

        // 2.2 查询当前 projectNo + subjectId 下的所有模块并映射 simple field 关系
        List<SysModule> modules =
                sysModuleMapper.selectList(
                        new LambdaQueryWrapper<SysModule>()
                                .eq(SysModule::getProjectNo, projectNo)
                                .eq(SysModule::getSubjectId, subjectId));

        Map<String, List<SysFieldResp.SysFieldModuleResp>> columnModulesMap =
                Collections.emptyMap();
        if (!modules.isEmpty()) {
            List<Long> moduleIds = modules.stream().map(SysModule::getId).toList();
            List<SysModuleSimpleField> simpleFields =
                    sysModuleSimpleFieldMapper.selectList(
                            new LambdaQueryWrapper<SysModuleSimpleField>()
                                    .in(SysModuleSimpleField::getModuleId, moduleIds)
                                    .eq(SysModuleSimpleField::getTableName, tableName));

            Map<Long, SysModule> moduleMap =
                    modules.stream()
                            .collect(Collectors.toMap(SysModule::getId, m -> m, (m1, m2) -> m1));

            columnModulesMap =
                    simpleFields.stream()
                            .filter(sf -> moduleMap.containsKey(sf.getModuleId()))
                            .collect(
                                    Collectors.groupingBy(
                                            SysModuleSimpleField::getColumnName,
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

        // 3. 以元数据驱动进行合并
        Map<String, List<SysFieldResp.SysFieldModuleResp>> finalColumnModulesMap = columnModulesMap;
        return columnRows.stream()
                .filter(
                        row ->
                                columnName == null
                                        || columnName.isBlank()
                                        || columnName.equals(row.get("COLUMN_NAME")))
                .map(
                        row -> {
                            String colName = (String) row.get("COLUMN_NAME");
                            String columnComment = (String) row.get("COLUMN_COMMENT");
                            SysField config = configMap.get(colName);

                            if (config != null) {
                                // 已有配置
                                return SysFieldResp.builder()
                                        .id(config.getId())
                                        .projectNo(projectNo)
                                        .subjectId(subjectId)
                                        .tableName(tableName)
                                        .columnName(colName)
                                        .displayName(config.getDisplayName())
                                        .relationBusinessNo(config.getRelationBusinessNo())
                                        .relationName(config.getRelationName())
                                        .encrypted(config.getEncrypted())
                                        .fieldValues(
                                                valueMap.getOrDefault(
                                                        config.getId(), Collections.emptyList()))
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
                                        .displayName(
                                                columnComment != null && !columnComment.isEmpty()
                                                        ? columnComment
                                                        : colName)
                                        .encrypted(0)
                                        .fieldValues(Collections.emptyList())
                                        .modules(
                                                finalColumnModulesMap.getOrDefault(
                                                        colName, Collections.emptyList()))
                                        .build();
                            }
                        })
                .toList();
    }

    public List<SysField> querySysFieldEntities(
            String projectNo, Long subjectId, String tableName) {
        return sysFieldMapper.selectList(
                new LambdaQueryWrapper<SysField>()
                        .eq(SysField::getProjectNo, projectNo)
                        .eq(SysField::getSubjectId, subjectId)
                        .eq(SysField::getTableName, tableName)
                        .eq(SysField::getDeleted, 0));
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

        // 更新时查询旧的 relationBusinessNo
        String oldBusinessNo = null;
        if (req.getId() != null) {
            SysField oldField = sysFieldMapper.selectById(req.getId());
            if (oldField != null) {
                oldBusinessNo = oldField.getRelationBusinessNo();
            }
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
                        .deleted(0)
                        .build();

        if (entity.getId() == null) {
            sysFieldMapper.insert(entity);
        } else {
            sysFieldMapper.updateById(entity);
        }

        Long fieldId = entity.getId();
        processFieldValues(fieldId, req.getFieldValues());

        // 通知 Word 系统字段绑定关系变更
        if (req.getRelationBusinessNo() != null && !req.getRelationBusinessNo().isBlank()) {
            saveToWordSystem(oldBusinessNo, req.getRelationBusinessNo(), tableName, subjectId);
        }

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

        // 查询关联的扩展值信息
        List<SysFieldValueResp> fieldValues =
                sysFieldValueMapper
                        .selectList(
                                new LambdaQueryWrapper<SysFieldValue>()
                                        .eq(SysFieldValue::getFieldId, fieldId)
                                        .eq(SysFieldValue::getDeleted, 0))
                        .stream()
                        .map(
                                v ->
                                        SysFieldValueResp.builder()
                                                .id(v.getId())
                                                .fieldId(v.getFieldId())
                                                .moduleId(v.getModuleId())
                                                .moduleName(v.getModuleName())
                                                .fieldValue(v.getFieldValue())
                                                .approvalChainTypeId(v.getApprovalChainTypeId())
                                                .sortOrder(v.getSortOrder())
                                                .enabled(v.getEnabled())
                                                .build())
                        .toList();

        return SysFieldResp.builder()
                .id(field.getId())
                .projectNo(projectNo)
                .subjectId(subjectId)
                .tableName(tableName)
                .columnName(field.getColumnName())
                .displayName(field.getDisplayName())
                .relationBusinessNo(field.getRelationBusinessNo())
                .relationName(field.getRelationName())
                .encrypted(field.getEncrypted())
                .fieldValues(fieldValues)
                .build();
    }

    /** 将字段绑定关系同步到 Word 系统 */
    private void saveToWordSystem(
            String oldBusinessNo, String businessNo, String tableName, Long subjectId) {
        try {
            HashMap<String, Object> params = new HashMap<>();
            params.put("old_business_no", oldBusinessNo);
            params.put("business_no", businessNo);

            SysModule sysModule =
                    sysModuleMapper
                            .selectList(
                                    new LambdaQueryWrapper<SysModule>()
                                            .eq(SysModule::getSubjectId, subjectId)
                                            .eq(SysModule::getPrimaryTable, tableName))
                            .stream()
                            .findFirst()
                            .orElse(null);

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
}
