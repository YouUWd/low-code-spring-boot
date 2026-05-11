package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.api.SysFieldApi;
import com.jdec.platform.config.api.dto.request.SysFieldSaveReq;
import com.jdec.platform.config.api.dto.request.SysFieldValueSaveReq;
import com.jdec.platform.config.api.dto.response.ColumnInfoResp;
import com.jdec.platform.config.api.dto.response.DatabaseSchemaResp;
import com.jdec.platform.config.api.dto.response.SysFieldResp;
import com.jdec.platform.config.api.dto.response.SysFieldValueResp;
import com.jdec.platform.config.api.dto.response.TableInfoResp;
import com.jdec.platform.config.biz.entity.SysField;
import com.jdec.platform.config.biz.entity.SysFieldValue;
import com.jdec.platform.config.biz.mapper.SysFieldMapper;
import com.jdec.platform.config.biz.mapper.SysFieldValueMapper;
import com.jdec.platform.shared.datasource.DataSource;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final JdbcTemplate jdbcTemplate;

    // ==================== 元数据查询逻辑 ====================

    @Override
    public List<TableInfoResp> getAllTables(String tenantId) {
        log.debug("查询数据源 {} 的所有表", tenantId);

        String sql =
                """
            SELECT TABLE_NAME, TABLE_COMMENT
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = ?
            ORDER BY TABLE_NAME
            """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tenantId);
            return rows.stream()
                    .map(
                            row ->
                                    TableInfoResp.builder()
                                            .tableName((String) row.get("TABLE_NAME"))
                                            .tableComment((String) row.get("TABLE_COMMENT"))
                                            .build())
                    .toList();
        } catch (Exception e) {
            log.error("查询数据源 {} 的表信息失败", tenantId, e);
            throw new RuntimeException("查询表信息失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ColumnInfoResp> getTableColumns(String tenantId, String tableName) {
        log.debug("查询表 {}.{} 的列信息", tenantId, tableName);

        String sql =
                """
            SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT, IS_NULLABLE, COLUMN_KEY, EXTRA
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
            ORDER BY ORDINAL_POSITION
            """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tenantId, tableName);
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

            enrichColumnsWithSysField(tenantId, tableName, columns);

            return columns;
        } catch (Exception e) {
            log.error("查询表 {}.{} 的列信息失败", tenantId, tableName, e);
            throw new RuntimeException("查询列信息失败: " + e.getMessage(), e);
        }
    }

    private void enrichColumnsWithSysField(
            String tenantId, String tableName, List<ColumnInfoResp> columns) {
        try {
            List<SysField> sysFields = querySysFieldEntities(tenantId, tableName);
            if (sysFields.isEmpty()) {
                columns.forEach(
                        column ->
                                column.setDisplayName(
                                        column.getColumnComment() != null
                                                ? column.getColumnComment()
                                                : column.getColumnName()));
                return;
            }

            List<Long> fieldIds = sysFields.stream().map(SysField::getId).toList();
            // 扩展值表不再通过 tenant_id 过滤，直接通过关联的 field_id 获取（field_id 已隐含租户信息）
            List<SysFieldValue> fieldValues =
                    sysFieldValueMapper.selectList(
                            new LambdaQueryWrapper<SysFieldValue>()
                                    .in(SysFieldValue::getFieldId, fieldIds)
                                    .eq(SysFieldValue::getDeleted, 0));

            Map<Long, List<SysFieldValueResp>> valueMap =
                    fieldValues.stream()
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
                            .collect(Collectors.groupingBy(SysFieldValueResp::getFieldId));

            Map<String, SysField> configMap = new HashMap<>(sysFields.size());
            sysFields.forEach(sysField -> configMap.put(sysField.getColumnName(), sysField));

            columns.forEach(
                    column -> {
                        SysField config = configMap.get(column.getColumnName());
                        if (config != null) {
                            column.setDisplayName(config.getDisplayName());
                            column.setRelationBusinessNo(config.getRelationBusinessNo());
                            column.setRelationName(config.getRelationName());
                            column.setFieldValues(
                                    valueMap.getOrDefault(config.getId(), Collections.emptyList()));
                        } else {
                            column.setDisplayName(
                                    column.getColumnComment() != null
                                            ? column.getColumnComment()
                                            : column.getColumnName());
                            column.setFieldValues(Collections.emptyList());
                        }
                    });
        } catch (Exception e) {
            log.warn("增强表 {} 的列信息失败: {}", tableName, e.getMessage());
        }
    }

    // ==================== 字段配置持久化逻辑 ====================

    @Override
    public List<SysFieldResp> querySysFields(String tenantId, String tableName) {
        List<SysField> list = querySysFieldEntities(tenantId, tableName);
        return list.stream()
                .map(
                        e ->
                                SysFieldResp.builder()
                                        .id(e.getId())
                                        .tenantId(e.getTenantId())
                                        .tableName(e.getTableName())
                                        .columnName(e.getColumnName())
                                        .displayName(e.getDisplayName())
                                        .relationBusinessNo(e.getRelationBusinessNo())
                                        .relationName(e.getRelationName())
                                        .build())
                .toList();
    }

    public List<SysField> querySysFieldEntities(String tenantId, String tableName) {
        return sysFieldMapper.selectList(
                new LambdaQueryWrapper<SysField>()
                        .eq(SysField::getTenantId, tenantId)
                        .eq(SysField::getTableName, tableName)
                        .eq(SysField::getDeleted, 0));
    }

    @Override
    @Transactional
    public void saveSysField(String tenantId, String tableName, SysFieldSaveReq req) {
        log.info("开始保存字段配置及扩展值: {}/{}", tableName, req.getColumnName());

        SysField entity =
                SysField.builder()
                        .id(req.getId())
                        .tenantId(tenantId)
                        .tableName(tableName)
                        .columnName(req.getColumnName())
                        .displayName(req.getDisplayName())
                        .relationBusinessNo(req.getRelationBusinessNo())
                        .relationName(req.getRelationName())
                        .deleted(0)
                        .build();

        if (entity.getId() == null) {
            sysFieldMapper.insert(entity);
        } else {
            sysFieldMapper.updateById(entity);
        }

        Long fieldId = entity.getId();
        processFieldValues(fieldId, req.getFieldValues());
    }

    /** 处理字段扩展值的级联保存 */
    private void processFieldValues(Long fieldId, List<SysFieldValueSaveReq> valueReqs) {
        // 仅根据 fieldId 清理旧配置
        sysFieldValueMapper.delete(
            new LambdaQueryWrapper<SysFieldValue>()
                .eq(SysFieldValue::getFieldId, fieldId)
        );

        if (valueReqs == null || valueReqs.isEmpty()) {
            return;
        }

        for (SysFieldValueSaveReq vReq : valueReqs) {
            SysFieldValue vEntity = SysFieldValue.builder()
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
