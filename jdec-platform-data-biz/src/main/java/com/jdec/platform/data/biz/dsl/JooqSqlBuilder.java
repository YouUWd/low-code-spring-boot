package com.jdec.platform.data.biz.dsl;

import com.jdec.platform.config.api.dto.common.ModuleSimpleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleTableDTO;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * <p>业务规范：所有业务关系均以主表为主体发起。
 *
 * <ul>
 *   <li>{@code join_left_field}：主表（发起方）的关联字段（如主表 {@code id} 或 {@code teacher_id}）
 *   <li>{@code join_right_field}：关联从表（接收方）的关联字段（如从表 {@code course_id} 或 {@code id}）
 * </ul>
 */
@Component
public class JooqSqlBuilder {

    /** 构建精确计数 COUNT 查询，避免衍生表 Duplicate column name 报错 */
    public long fetchCount(
            DSLContext dsl, SysModuleCompleteResp completeResp, Condition condition) {
        String primaryTable = getPrimaryTableName(completeResp);
        Table<?> fromTable = DSL.table(DSL.name(primaryTable));
        SelectJoinStep<?> countStep = dsl.selectCount().from(fromTable);
        countStep = applyJoins(countStep, primaryTable, completeResp.getModuleTables());

        Long total = countStep.where(condition).fetchOne(0, Long.class);
        return total != null ? total : 0L;
    }

    /** 构建包含主表与 1:1/N:1 关联从表的 SelectJoinStep (支持元数据字段安全投影) */
    public SelectJoinStep<Record> buildSelectFrom(
            DSLContext dsl, SysModuleCompleteResp completeResp) {
        String primaryTable = getPrimaryTableName(completeResp);
        Table<?> fromTable = DSL.table(DSL.name(primaryTable));

        List<Field<?>> selectFields =
                resolveSelectFields(
                        primaryTable,
                        completeResp.getModuleTables(),
                        completeResp.getSimpleFields());
        SelectJoinStep<Record> query =
                selectFields.isEmpty()
                        ? dsl.select(DSL.asterisk()).from(fromTable)
                        : dsl.select(selectFields).from(fromTable);

        return applyJoins(query, primaryTable, completeResp.getModuleTables());
    }

    /** 应用 1:1 与 N:1 物理 Join 连接 */
    @SuppressWarnings("unchecked")
    private <T extends Record> SelectJoinStep<T> applyJoins(
            SelectJoinStep<?> queryStep, String primaryTable, List<ModuleTableDTO> moduleTables) {
        SelectJoinStep<T> current = (SelectJoinStep<T>) queryStep;
        if (moduleTables == null) {
            return current;
        }

        for (ModuleTableDTO tableDto : moduleTables) {
            if (primaryTable.equalsIgnoreCase(tableDto.getTableName())) {
                continue;
            }

            String relationType = tableDto.getRelationType();
            if ("1:1".equalsIgnoreCase(relationType)
                    || "N:1".equalsIgnoreCase(relationType)
                    || "ONE_TO_ONE".equalsIgnoreCase(relationType)) {
                String joinTableName = tableDto.getTableName();
                String leftField = tableDto.getJoinLeftField();
                String rightField = tableDto.getJoinRightField();

                if (leftField != null
                        && !leftField.isBlank()
                        && rightField != null
                        && !rightField.isBlank()) {
                    current =
                            current.leftJoin(DSL.table(DSL.name(joinTableName)))
                                    .on(
                                            DSL.field(DSL.name(primaryTable, leftField))
                                                    .eq(
                                                            DSL.field(
                                                                    DSL.name(
                                                                            joinTableName,
                                                                            rightField))));
                }
            }
        }
        return current;
    }

    /** 提取主表表名 */
    private String getPrimaryTableName(SysModuleCompleteResp completeResp) {
        List<ModuleTableDTO> moduleTables = completeResp.getModuleTables();
        ModuleTableDTO primaryTableDto =
                moduleTables != null
                        ? moduleTables.stream()
                                .filter(t -> t.getIsPrimary() != null && t.getIsPrimary() == 1)
                                .findFirst()
                                .orElse(moduleTables.isEmpty() ? null : moduleTables.get(0))
                        : null;
        return primaryTableDto != null ? primaryTableDto.getTableName() : "";
    }

    /** 按元数据配置的 simpleFields 解析安全投影字段集合 (仅投影主表与参与物理 Join 的 1:1/N:1 从表字段) */
    private List<Field<?>> resolveSelectFields(
            String primaryTable,
            List<ModuleTableDTO> moduleTables,
            List<ModuleSimpleFieldDTO> simpleFields) {
        List<Field<?>> fields = new ArrayList<>();
        if (simpleFields == null || simpleFields.isEmpty()) {
            return fields;
        }

        // 收集所有参与主查询物理投影的有效表名（主表 + 1:1/N:1 从表）
        Set<String> validJoinedTables = new HashSet<>();
        validJoinedTables.add(primaryTable.toLowerCase());

        if (moduleTables != null) {
            for (ModuleTableDTO t : moduleTables) {
                String rel = t.getRelationType();
                if ("1:1".equalsIgnoreCase(rel)
                        || "N:1".equalsIgnoreCase(rel)
                        || "ONE_TO_ONE".equalsIgnoreCase(rel)) {
                    if (t.getTableName() != null) {
                        validJoinedTables.add(t.getTableName().toLowerCase());
                    }
                }
            }
        }

        Set<String> addedAliases = new HashSet<>();
        for (ModuleSimpleFieldDTO f : simpleFields) {
            String tName =
                    (f.getTableName() != null && !f.getTableName().isBlank())
                            ? f.getTableName()
                            : primaryTable;

            // 仅投影属于主表或 1:1/N:1 已连接表的字段，跳过 1:N 从表字段
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

        // 默认确保所有参与 Join 的 1:1 与 N:1 从表的主键 id 均被投影
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
