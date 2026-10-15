package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.common.api.dto.request.FieldSortItem;
import com.jdec.platform.common.api.dto.request.MenuFlatFieldFilterItem;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.data.api.dto.request.SortItem;
import com.jdec.platform.data.api.dto.request.TableSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** 将配置域 fieldId 转换为 data-api 使用的物理表名与列名。 */
final class PhysicalQueryMapper {

    private PhysicalQueryMapper() {}

    static PhysicalFieldRef resolve(ModuleTreeDTO tree, Long fieldId) {
        if (fieldId == null) {
            throw new IllegalArgumentException("字段 ID 不能为空");
        }
        if (tree == null) {
            throw new IllegalArgumentException("模块元数据不能为空");
        }

        ModuleFieldDTO field = tree.getField(fieldId);
        Long moduleId = tree.getModuleIdByFieldId(fieldId);
        if (field == null || moduleId == null) {
            throw new IllegalArgumentException("字段 [" + fieldId + "] 不属于当前模块树");
        }

        ModuleInfo module = tree.getModule(moduleId);
        String tableName = field.getTableName();
        if (tableName == null || tableName.isBlank()) {
            tableName = module != null ? module.getPrimaryTable() : null;
        }
        String columnName = field.getColumnName();
        if (tableName == null
                || tableName.isBlank()
                || columnName == null
                || columnName.isBlank()) {
            throw new IllegalArgumentException("字段 [" + fieldId + "] 未配置有效的物理表名或列名");
        }
        return new PhysicalFieldRef(fieldId, moduleId, tableName.trim(), columnName.trim());
    }

    record ModuleTableKey(Long moduleId, String tableName) {}

    static List<TableSpec> toTableSpecs(ModuleTreeDTO tree, Collection<Long> fieldIds) {
        Map<ModuleTableKey, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
        if (fieldIds != null) {
            for (Long fieldId : fieldIds) {
                if (fieldId == null) {
                    continue;
                }
                PhysicalFieldRef ref = resolve(tree, fieldId);
                grouped.computeIfAbsent(
                                new ModuleTableKey(ref.moduleId(), ref.tableName()),
                                key -> new LinkedHashSet<>())
                        .add(ref.columnName());
            }
        }
        List<TableSpec> specs = new ArrayList<>();
        for (Map.Entry<ModuleTableKey, LinkedHashSet<String>> entry : grouped.entrySet()) {
            specs.add(
                    TableSpec.of(
                            entry.getKey().moduleId(),
                            entry.getKey().tableName(),
                            new ArrayList<>(entry.getValue())));
        }
        return specs;
    }

    static List<FilterItem> toTableFilters(
            ModuleTreeDTO tree, Collection<MenuFlatFieldFilterItem> filters) {
        List<FilterItem> result = new ArrayList<>();
        if (filters == null) {
            return result;
        }
        for (MenuFlatFieldFilterItem filter : filters) {
            if (filter != null) {
                PhysicalFieldRef ref = resolve(tree, filter.getFieldId());
                result.add(
                        FilterItem.builder()
                                .moduleId(ref.moduleId())
                                .tableName(ref.tableName())
                                .columnName(ref.columnName())
                                .operator(
                                        filter.getOperator() != null ? filter.getOperator() : "EQ")
                                .value(filter.getValue())
                                .build());
            }
        }
        return result;
    }

    static List<SortItem> toTableSorts(ModuleTreeDTO tree, Collection<FieldSortItem> sorts) {
        List<SortItem> result = new ArrayList<>();
        if (sorts == null) {
            return result;
        }
        for (FieldSortItem sort : sorts) {
            if (sort != null) {
                PhysicalFieldRef ref = resolve(tree, sort.getFieldId());
                result.add(
                        SortItem.builder()
                                .moduleId(ref.moduleId())
                                .tableName(ref.tableName())
                                .columnName(ref.columnName())
                                .direction(
                                        sort.getDirection() != null ? sort.getDirection() : "ASC")
                                .build());
            }
        }
        return result;
    }

    static FilterItem filter(ModuleTreeDTO tree, Long fieldId, String operator, Object value) {
        PhysicalFieldRef ref = resolve(tree, fieldId);
        return FilterItem.builder()
                .moduleId(ref.moduleId())
                .tableName(ref.tableName())
                .columnName(ref.columnName())
                .operator(operator)
                .value(value)
                .build();
    }

    record PhysicalFieldRef(Long fieldId, Long moduleId, String tableName, String columnName) {}
}
