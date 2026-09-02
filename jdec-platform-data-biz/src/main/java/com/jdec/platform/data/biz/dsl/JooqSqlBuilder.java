package com.jdec.platform.data.biz.dsl;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.api.dto.response.SysModuleMetaResp;
import java.util.*;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * jOOQ 多表 Join 组装器
 *
 * <p>基于全局表关联关系 (TableRelationDTO) 与模块字段列表动态构建：
 *
 * <ul>
 *   <li>主表：以 fields 排序后首条记录的 tableName 为准
 *   <li>从表 Join 条件：main_table.main_field = join_table.join_field
 * </ul>
 */
@Component
public class JooqSqlBuilder {

    /** 构建精确计数 COUNT 查询，避免衍生表 Duplicate column name 报错 */
    public long fetchCount(DSLContext dsl, SysModuleMetaResp completeResp, Condition condition) {
        String primaryTable = getPrimaryTableName(completeResp);
        Table<?> fromTable = DSL.table(DSL.name(primaryTable));
        SelectJoinStep<?> countStep = dsl.selectCount().from(fromTable);
        countStep = applyJoins(countStep, primaryTable, completeResp);

        Long total = countStep.where(condition).fetchOne(0, Long.class);
        return total != null ? total : 0L;
    }

    /** 构建包含主表与 1:1/N:1 关联从表的 SelectJoinStep (支持元数据字段安全投影) */
    public SelectJoinStep<Record> buildSelectFrom(DSLContext dsl, SysModuleMetaResp completeResp) {
        String primaryTable = getPrimaryTableName(completeResp);
        Table<?> fromTable = DSL.table(DSL.name(primaryTable));

        List<Field<?>> selectFields = resolveSelectFields(primaryTable, completeResp);
        SelectJoinStep<Record> query =
                selectFields.isEmpty()
                        ? dsl.select(DSL.asterisk()).from(fromTable)
                        : dsl.select(selectFields).from(fromTable);

        return applyJoins(query, primaryTable, completeResp);
    }

    /** 统一计算当前模块实际发生物理 Join 的所有表集合 (严格要求该表在 fields 中有被配置) */
    public Set<String> calculateJoinedTables(String primaryTable, SysModuleMetaResp completeResp) {
        Set<String> joined = new HashSet<>();
        joined.add(primaryTable.toLowerCase());

        if (completeResp == null || completeResp.getTableRelations() == null) {
            return joined;
        }

        Set<String> involvedTables = new HashSet<>();
        if (completeResp.getFields() != null) {
            for (ModuleFieldDTO f : completeResp.getFields()) {
                if (f.getTableName() != null) {
                    involvedTables.add(f.getTableName().toLowerCase());
                }
            }
        }

        for (TableRelationDTO rel : completeResp.getTableRelations()) {
            String mainT = rel.getMainTable() != null ? rel.getMainTable().toLowerCase() : "";
            String joinT = rel.getJoinTable() != null ? rel.getJoinTable().toLowerCase() : "";
            String rType = rel.getRelationType() != null ? rel.getRelationType() : "";

            if ("1:1".equalsIgnoreCase(rType)) {
                if (primaryTable.equalsIgnoreCase(mainT) && involvedTables.contains(joinT)) {
                    joined.add(joinT);
                } else if (primaryTable.equalsIgnoreCase(joinT) && involvedTables.contains(mainT)) {
                    joined.add(mainT);
                }
            } else if ("1:N".equalsIgnoreCase(rType)) {
                // 当且仅当当前模块是从表 (joinTable)，且本模块字段列表包含主表 (mainTable) 时，才物理连接主表
                if (primaryTable.equalsIgnoreCase(joinT) && involvedTables.contains(mainT)) {
                    joined.add(mainT);
                }
            }
        }
        return joined;
    }

    /** 应用 1:1 与 N:1 物理 Join 连接 */
    @SuppressWarnings("unchecked")
    private <T extends Record> SelectJoinStep<T> applyJoins(
            SelectJoinStep<?> queryStep, String primaryTable, SysModuleMetaResp completeResp) {
        SelectJoinStep<T> current = (SelectJoinStep<T>) queryStep;
        List<TableRelationDTO> relations = completeResp.getTableRelations();
        if (relations == null || relations.isEmpty()) {
            return current;
        }

        Set<String> joinedTables = calculateJoinedTables(primaryTable, completeResp);
        Set<String> alreadyJoined = new HashSet<>();
        alreadyJoined.add(primaryTable.toLowerCase());

        for (TableRelationDTO rel : relations) {
            String mainTable = rel.getMainTable();
            String mainField = rel.getMainField();
            String joinTable = rel.getJoinTable();
            String joinField = rel.getJoinField();
            String rType = rel.getRelationType();

            if ("1:1".equalsIgnoreCase(rType)) {
                if (primaryTable.equalsIgnoreCase(mainTable)
                        && joinedTables.contains(joinTable.toLowerCase())
                        && alreadyJoined.add(joinTable.toLowerCase())) {
                    current =
                            current.leftJoin(DSL.table(DSL.name(joinTable)))
                                    .on(
                                            DSL.field(DSL.name(mainTable, mainField))
                                                    .eq(DSL.field(DSL.name(joinTable, joinField))));
                } else if (primaryTable.equalsIgnoreCase(joinTable)
                        && joinedTables.contains(mainTable.toLowerCase())
                        && alreadyJoined.add(mainTable.toLowerCase())) {
                    current =
                            current.leftJoin(DSL.table(DSL.name(mainTable)))
                                    .on(
                                            DSL.field(DSL.name(joinTable, joinField))
                                                    .eq(DSL.field(DSL.name(mainTable, mainField))));
                }
            } else if ("1:N".equalsIgnoreCase(rType)) {
                if (primaryTable.equalsIgnoreCase(joinTable)
                        && joinedTables.contains(mainTable.toLowerCase())
                        && alreadyJoined.add(mainTable.toLowerCase())) {
                    current =
                            current.leftJoin(DSL.table(DSL.name(mainTable)))
                                    .on(
                                            DSL.field(DSL.name(joinTable, joinField))
                                                    .eq(DSL.field(DSL.name(mainTable, mainField))));
                }
            }
        }
        return current;
    }

    /** 提取主表表名 (以 fields 的第 1 条记录为准) */
    public String getPrimaryTableName(SysModuleMetaResp completeResp) {
        if (completeResp == null
                || completeResp.getFields() == null
                || completeResp.getFields().isEmpty()) {
            return "";
        }
        return completeResp.getFields().get(0).getTableName();
    }

    /** 按元数据配置的 fields 解析安全投影字段集合 (仅投影主表与参与物理 Join 的 1:1/N:1 从表字段) */
    private List<Field<?>> resolveSelectFields(
            String primaryTable, SysModuleMetaResp completeResp) {
        List<Field<?>> fields = new ArrayList<>();
        List<ModuleFieldDTO> configuredFields = completeResp.getFields();
        if (configuredFields == null || configuredFields.isEmpty()) {
            return fields;
        }

        Set<String> validJoinedTables = calculateJoinedTables(primaryTable, completeResp);

        Set<String> addedAliases = new HashSet<>();
        for (ModuleFieldDTO f : configuredFields) {
            String tName =
                    (f.getTableName() != null && !f.getTableName().isBlank())
                            ? f.getTableName()
                            : primaryTable;

            // 仅投影属于主表或真实发生物理 Join 的表的字段
            if (!validJoinedTables.contains(tName.toLowerCase())) {
                continue;
            }

            String cName = f.getColumnName();
            if (cName == null || cName.isBlank()) {
                continue;
            }

            String alias = cName.toLowerCase();
            // 防止重复别名
            if (addedAliases.add(alias)) {
                fields.add(DSL.field(DSL.name(tName, cName)).as(cName));
            }
        }

        // 确保主表主键 id 始终存在于首位
        if (!addedAliases.contains("id")) {
            fields.add(0, DSL.field(DSL.name(primaryTable, "id")).as("id"));
        }

        // 确保所有真实参与 Join 的表的主键 id 均被投影
        for (String joinedTable : validJoinedTables) {
            String pkAlias = joinedTable + "__id";
            if (!primaryTable.equalsIgnoreCase(joinedTable)
                    && addedAliases.add(pkAlias.toLowerCase())) {
                fields.add(DSL.field(DSL.name(joinedTable, "id")).as(pkAlias));
            }
        }

        return fields;
    }
}
