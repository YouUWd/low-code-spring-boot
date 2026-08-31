package com.jdec.platform.data.biz.dsl;

import com.jdec.platform.config.api.dto.common.ModuleTableHeaderDTO;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** jOOQ 动态条件组装器 */
@Component
public class JooqConditionBuilder {

    /** 根据前端 filters 与 tableHeader 配置构建查询过滤条件 */
    public Condition buildConditions(
            String primaryTable,
            Map<String, Object> filters,
            List<ModuleTableHeaderDTO> headers,
            Long subjectId,
            SysModuleCompleteResp completeResp) {

        List<Condition> conditions = new ArrayList<>();

        // 1. 基础主体隔离（仅在主表配置了 subject_id 字段时才添加过滤条件）
        boolean hasSubjectId =
                completeResp != null
                        && completeResp.getSimpleFields() != null
                        && completeResp.getSimpleFields().stream()
                                .anyMatch(
                                        f ->
                                                primaryTable.equalsIgnoreCase(f.getTableName())
                                                        && "subject_id"
                                                                .equalsIgnoreCase(
                                                                        f.getColumnName()));

        if (hasSubjectId && subjectId != null && subjectId > 0) {
            conditions.add(DSL.field(DSL.name(primaryTable, "subject_id")).eq(subjectId));
        }

        // 2. 动态过滤条件
        if (filters != null && !filters.isEmpty()) {
            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                String fieldName = entry.getKey();
                Object value = entry.getValue();
                if (value == null || "".equals(value)) {
                    continue;
                }

                // 查找该字段所属表
                String tableName = primaryTable;
                String searchType = "";
                if (headers != null) {
                    for (ModuleTableHeaderDTO h : headers) {
                        if (fieldName.equalsIgnoreCase(h.getField())) {
                            if (h.getTable() != null
                                    && !"*".equals(h.getTable())
                                    && !h.getTable().isEmpty()) {
                                tableName = h.getTable();
                            }
                            searchType = h.getSearchType() != null ? h.getSearchType() : "";
                            break;
                        }
                    }
                }

                if ("singleFuzzySelect".equalsIgnoreCase(searchType)
                        || "text".equalsIgnoreCase(searchType)) {
                    conditions.add(
                            DSL.field(DSL.name(tableName, fieldName), String.class)
                                    .like("%" + value + "%"));
                } else if (value instanceof List<?> listVal) {
                    if (listVal.size() == 2
                            && ("dateRange".equalsIgnoreCase(searchType)
                                    || fieldName.contains("date")
                                    || fieldName.contains("Date"))) {
                        conditions.add(
                                DSL.field(DSL.name(tableName, fieldName))
                                        .between(listVal.get(0), listVal.get(1)));
                    } else if (!listVal.isEmpty()) {
                        conditions.add(DSL.field(DSL.name(tableName, fieldName)).in(listVal));
                    }
                } else {
                    conditions.add(DSL.field(DSL.name(tableName, fieldName)).eq(value));
                }
            }
        }

        return conditions.isEmpty() ? DSL.noCondition() : DSL.and(conditions);
    }
}
