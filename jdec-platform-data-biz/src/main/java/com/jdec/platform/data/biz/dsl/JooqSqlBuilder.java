package com.jdec.platform.data.biz.dsl;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleTableHeaderDTO;
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
    public long fetchCount(
            DSLContext dsl, SysModuleMetaResp completeResp, Condition condition, String viewMode) {
        String primaryTable = getPrimaryTableName(completeResp);
        Table<?> fromTable = DSL.table(DSL.name(primaryTable));
        SelectJoinStep<?> countStep = dsl.selectCount().from(fromTable);
        countStep = applyJoins(countStep, primaryTable, completeResp, viewMode);

        Long total = countStep.where(condition).fetchOne(0, Long.class);
        return total != null ? total : 0L;
    }

    public long fetchCount(DSLContext dsl, SysModuleMetaResp completeResp, Condition condition) {
        return fetchCount(dsl, completeResp, condition, "LIST");
    }

    /** 构建包含主表与 1:1/N:1 关联从表的 SelectJoinStep (由 viewMode 驱动从 headers 或 fields 取字段与表) */
    public SelectJoinStep<Record> buildSelectFrom(
            DSLContext dsl, SysModuleMetaResp completeResp, String viewMode) {
        String primaryTable = getPrimaryTableName(completeResp);
        Table<?> fromTable = DSL.table(DSL.name(primaryTable));

        List<Field<?>> selectFields = resolveSelectFields(primaryTable, completeResp, viewMode);
        SelectJoinStep<Record> query =
                selectFields.isEmpty()
                        ? dsl.select(DSL.asterisk()).from(fromTable)
                        : dsl.select(selectFields).from(fromTable);

        return applyJoins(query, primaryTable, completeResp, viewMode);
    }

    public SelectJoinStep<Record> buildSelectFrom(DSLContext dsl, SysModuleMetaResp completeResp) {
        return buildSelectFrom(dsl, completeResp, "LIST");
    }

    /** 计算当前模块实际发生物理 Join 的表集合 (LIST 从 headers 取，DETAIL 从 fields 取) */
    public Set<String> calculateJoinedTables(
            String primaryTable, SysModuleMetaResp completeResp, String viewMode) {
        Set<String> joined = new HashSet<>();
        joined.add(primaryTable.toLowerCase());

        if (completeResp == null || completeResp.getTableRelations() == null) {
            return joined;
        }

        boolean isDetailMode = "DETAIL".equalsIgnoreCase(viewMode);
        boolean hasFields = completeResp.getFields() != null && !completeResp.getFields().isEmpty();

        Set<String> involvedTables = new HashSet<>();
        if (isDetailMode && hasFields) {
            // DETAIL 场景：从当前模块 sys_module_field 中取表信息
            for (ModuleFieldDTO f : completeResp.getFields()) {
                if (f.getTableName() != null && !f.getTableName().isBlank()) {
                    involvedTables.add(f.getTableName().toLowerCase());
                }
            }
        } else {
            // LIST 场景 (或虚拟空白模块无 fields 自适应回退)：从 sys_module_header 中取模块与表信息
            if (completeResp.getModuleHeaders() != null) {
                for (ModuleTableHeaderDTO h : completeResp.getModuleHeaders()) {
                    if (h.getTable() == null || h.getTable().isBlank()) {
                        throw new IllegalStateException(
                                String.format(
                                        "模块表头元数据配置异常：物理表名不能为空 (headerName=%s, field=%s)",
                                        h.getName(), h.getField()));
                    }
                    involvedTables.add(h.getTable().toLowerCase());
                }
            }
        }

        // 主查询仅需物理 Join 单行伴生表/父维表（1:1 / N:1），1:N 深度子模块数据由后续分层异步接口组装
        boolean changed = true;
        int maxRounds = 5;
        while (changed && maxRounds-- > 0) {
            changed = false;
            for (TableRelationDTO rel : completeResp.getTableRelations()) {
                String mainT = rel.getMainTable() != null ? rel.getMainTable().toLowerCase() : "";
                String joinT = rel.getJoinTable() != null ? rel.getJoinTable().toLowerCase() : "";
                String rType =
                        rel.getRelationType() != null ? rel.getRelationType().toUpperCase() : "";

                if ("1:1".equals(rType)) {
                    if (joined.contains(mainT)
                            && involvedTables.contains(joinT)
                            && joined.add(joinT)) {
                        changed = true;
                    } else if (joined.contains(joinT)
                            && involvedTables.contains(mainT)
                            && joined.add(mainT)) {
                        changed = true;
                    }
                } else if ("N:1".equals(rType)) {
                    // 主表持有从表外键，从表是伴生平铺维表
                    if (joined.contains(mainT)
                            && involvedTables.contains(joinT)
                            && joined.add(joinT)) {
                        changed = true;
                    }
                } else if ("1:N".equals(rType)) {
                    // 当前已连从表，需要向外连接主表/父维表 (如从 student 连到 clazz)
                    if (joined.contains(joinT)
                            && involvedTables.contains(mainT)
                            && joined.add(mainT)) {
                        changed = true;
                    }
                }
            }
        }

        return joined;
    }

    /** 查找目标表与主表之间的直接关联定义 (无论是主表连从表，还是从表连主表) */
    private TableRelationDTO findDirectRelationWithPrimary(
            String primaryTable, String targetTable, List<TableRelationDTO> relations) {
        if (relations == null || relations.isEmpty()) {
            return null;
        }
        for (TableRelationDTO rel : relations) {
            String mainT = rel.getMainTable() != null ? rel.getMainTable().toLowerCase() : "";
            String joinT = rel.getJoinTable() != null ? rel.getJoinTable().toLowerCase() : "";
            if ((primaryTable.equalsIgnoreCase(mainT) && targetTable.equalsIgnoreCase(joinT))
                    || (primaryTable.equalsIgnoreCase(joinT)
                            && targetTable.equalsIgnoreCase(mainT))) {
                return rel;
            }
        }
        return null;
    }

    public Set<String> calculateJoinedTables(String primaryTable, SysModuleMetaResp completeResp) {
        return calculateJoinedTables(primaryTable, completeResp, "LIST");
    }

    /** 应用 1:1 与 N:1 物理 Join 连接 */
    @SuppressWarnings("unchecked")
    private <T extends Record> SelectJoinStep<T> applyJoins(
            SelectJoinStep<?> queryStep,
            String primaryTable,
            SysModuleMetaResp completeResp,
            String viewMode) {
        SelectJoinStep<T> current = (SelectJoinStep<T>) queryStep;
        List<TableRelationDTO> relations = completeResp.getTableRelations();
        if (relations == null || relations.isEmpty()) {
            return current;
        }

        boolean isDetailMode = "DETAIL".equalsIgnoreCase(viewMode);
        Set<String> joinedTables = calculateJoinedTables(primaryTable, completeResp, viewMode);

        if (isDetailMode) {
            // DETAIL 模式：以主表为单一核心直接连入
            for (String joinT : joinedTables) {
                if (joinT.equalsIgnoreCase(primaryTable)) {
                    continue;
                }
                TableRelationDTO rel =
                        findDirectRelationWithPrimary(primaryTable, joinT, relations);
                if (rel == null) {
                    continue;
                }

                String mainTable = rel.getMainTable();
                String mainField = rel.getMainField();
                String joinTable = rel.getJoinTable();
                String joinField = rel.getJoinField();

                if (primaryTable.equalsIgnoreCase(mainTable)) {
                    current =
                            current.leftJoin(DSL.table(DSL.name(joinTable)))
                                    .on(
                                            DSL.field(DSL.name(mainTable, mainField))
                                                    .eq(DSL.field(DSL.name(joinTable, joinField))));
                } else {
                    current =
                            current.leftJoin(DSL.table(DSL.name(mainTable)))
                                    .on(
                                            DSL.field(DSL.name(joinTable, joinField))
                                                    .eq(DSL.field(DSL.name(mainTable, mainField))));
                }
            }
        } else {
            // LIST 模式：按照模块树拓扑 BFS 驱动物理 Join 扩展
            Set<String> alreadyJoined = new HashSet<>();
            alreadyJoined.add(primaryTable.toLowerCase());

            boolean hasNewJoin = true;
            int maxRounds = 5;
            while (hasNewJoin && maxRounds-- > 0) {
                hasNewJoin = false;
                for (TableRelationDTO rel : relations) {
                    String mainTable = rel.getMainTable();
                    String mainField = rel.getMainField();
                    String joinTable = rel.getJoinTable();
                    String joinField = rel.getJoinField();
                    String rType =
                            rel.getRelationType() != null
                                    ? rel.getRelationType().toUpperCase()
                                    : "";

                    if ("1:1".equals(rType) || "N:1".equals(rType)) {
                        if (alreadyJoined.contains(mainTable.toLowerCase())
                                && joinedTables.contains(joinTable.toLowerCase())
                                && alreadyJoined.add(joinTable.toLowerCase())) {
                            current =
                                    current.leftJoin(DSL.table(DSL.name(joinTable)))
                                            .on(
                                                    DSL.field(DSL.name(mainTable, mainField))
                                                            .eq(
                                                                    DSL.field(
                                                                            DSL.name(
                                                                                    joinTable,
                                                                                    joinField))));
                            hasNewJoin = true;
                        } else if (alreadyJoined.contains(joinTable.toLowerCase())
                                && joinedTables.contains(mainTable.toLowerCase())
                                && alreadyJoined.add(mainTable.toLowerCase())) {
                            current =
                                    current.leftJoin(DSL.table(DSL.name(mainTable)))
                                            .on(
                                                    DSL.field(DSL.name(joinTable, joinField))
                                                            .eq(
                                                                    DSL.field(
                                                                            DSL.name(
                                                                                    mainTable,
                                                                                    mainField))));
                            hasNewJoin = true;
                        }
                    } else if ("1:N".equals(rType)) {
                        // 从表连父维表 (如 student -> clazz)
                        if (alreadyJoined.contains(joinTable.toLowerCase())
                                && joinedTables.contains(mainTable.toLowerCase())
                                && alreadyJoined.add(mainTable.toLowerCase())) {
                            current =
                                    current.leftJoin(DSL.table(DSL.name(mainTable)))
                                            .on(
                                                    DSL.field(DSL.name(joinTable, joinField))
                                                            .eq(
                                                                    DSL.field(
                                                                            DSL.name(
                                                                                    mainTable,
                                                                                    mainField))));
                            hasNewJoin = true;
                        }
                    }
                }
            }
        }
        return current;
    }

    /**
     * 提取主表表名 (物理主表 / Root Entity) 1. 优先采用 sys_module 显式必填配置的 primaryTable； 2. 次选从当前模块
     * sys_module_field 中提取主表； 3. 虚拟空白模块 (无 fields)：进行全量列拓扑权重分析 (Topological Root
     * Scoring)，彻底排除表头列先后顺序干扰，精准推导根实体表！
     */
    public String getPrimaryTableName(SysModuleMetaResp completeResp) {
        if (completeResp == null) {
            return "";
        }

        // 1. 显式配置优先 (第一优先级)
        if (completeResp.getModule() != null
                && completeResp.getModule().getPrimaryTable() != null
                && !completeResp.getModule().getPrimaryTable().isBlank()) {
            return completeResp.getModule().getPrimaryTable().trim();
        }

        // 2. 物理字段配置提取
        if (completeResp.getFields() != null && !completeResp.getFields().isEmpty()) {
            for (ModuleFieldDTO f : completeResp.getFields()) {
                if (f.getTableName() != null && !f.getTableName().isBlank()) {
                    return f.getTableName().trim();
                }
            }
        }

        // 3. 全量列拓扑权重分析 (Topological Root Scoring): 排除列顺序干扰
        if (completeResp.getModuleHeaders() != null && !completeResp.getModuleHeaders().isEmpty()) {
            Set<String> involvedTables = new LinkedHashSet<>();
            for (ModuleTableHeaderDTO h : completeResp.getModuleHeaders()) {
                if (h.getTable() != null && !h.getTable().isBlank()) {
                    involvedTables.add(h.getTable().toLowerCase().trim());
                }
            }

            if (involvedTables.size() == 1) {
                return involvedTables.iterator().next();
            }

            // 结合关联拓扑计算各表的主实体中心度评分
            if (completeResp.getTableRelations() != null
                    && !completeResp.getTableRelations().isEmpty()) {
                Map<String, Integer> scoreMap = new HashMap<>();
                for (String tbl : involvedTables) {
                    scoreMap.put(tbl, 0);
                }

                for (TableRelationDTO rel : completeResp.getTableRelations()) {
                    if (rel.getMainTable() == null || rel.getJoinTable() == null) {
                        continue;
                    }
                    String mainT = rel.getMainTable().toLowerCase().trim();
                    String joinT = rel.getJoinTable().toLowerCase().trim();
                    String rType =
                            rel.getRelationType() != null
                                    ? rel.getRelationType().toUpperCase()
                                    : "";

                    // 在 1:1 或 1:N 关系中，mainTable 是父实体 / 业务主实体 (如 student -> student_course)
                    // 在 N:1 关系中，mainTable 持有外键，也是核心业务实体 (如 student -> clazz)
                    if ("1:N".equals(rType) || "1:1".equals(rType)) {
                        if (scoreMap.containsKey(mainT)) {
                            scoreMap.put(mainT, scoreMap.get(mainT) + 2);
                        }
                        if (scoreMap.containsKey(joinT)) {
                            scoreMap.put(joinT, scoreMap.get(joinT) - 1);
                        }
                    } else if ("N:1".equals(rType)) {
                        if (scoreMap.containsKey(mainT)) {
                            scoreMap.put(mainT, scoreMap.get(mainT) + 2);
                        }
                    }
                }

                String bestTable = null;
                int maxScore = Integer.MIN_VALUE;
                for (String tbl : involvedTables) {
                    int score = scoreMap.getOrDefault(tbl, 0);
                    if (score > maxScore) {
                        maxScore = score;
                        bestTable = tbl;
                    }
                }

                if (bestTable != null && maxScore > 0) {
                    return bestTable;
                }
            }

            // 兜底返回集合中第一个非空表名
            return involvedTables.iterator().next();
        }

        return "";
    }

    /**
     * 按 viewMode 解析安全投影字段集合 (LIST 取自 headers, DETAIL 取自当前模块 fields；若虚拟空白模块无 fields 则自适应回退至 headers)
     */
    private List<Field<?>> resolveSelectFields(
            String primaryTable, SysModuleMetaResp completeResp, String viewMode) {
        List<Field<?>> fields = new ArrayList<>();
        Set<String> validJoinedTables = calculateJoinedTables(primaryTable, completeResp, viewMode);
        Set<String> addedAliases = new HashSet<>();

        boolean isDetailMode = "DETAIL".equalsIgnoreCase(viewMode);
        boolean hasFields = completeResp.getFields() != null && !completeResp.getFields().isEmpty();

        if (isDetailMode && hasFields) {
            // 标准 DETAIL 模式：严格从当前模块 sys_module_field 中取表及字段信息
            List<ModuleFieldDTO> configuredFields = completeResp.getFields();
            for (ModuleFieldDTO f : configuredFields) {
                String tName =
                        (f.getTableName() != null && !f.getTableName().isBlank())
                                ? f.getTableName()
                                : primaryTable;

                if (!validJoinedTables.contains(tName.toLowerCase())) {
                    continue;
                }

                String cName = f.getColumnName();
                if (cName == null || cName.isBlank()) {
                    continue;
                }

                // 1. 若无别名冲突，保留原始 cName 别名（首选）
                String rawAlias = cName.toLowerCase();
                if (addedAliases.add(rawAlias)) {
                    fields.add(DSL.field(DSL.name(tName, cName)).as(cName));
                }

                // 2. 同时生成并投影带表名前缀的强唯一别名（如 clazz__clazz_name, student__name, student__id 等）
                String qualifiedAlias = (tName + "__" + cName).toLowerCase();
                if (addedAliases.add(qualifiedAlias)) {
                    fields.add(DSL.field(DSL.name(tName, cName)).as(tName + "__" + cName));
                }
            }
        } else {
            // LIST 模式 (或虚拟空白模块无 fields 自适应回退)：严格从 sys_module_header 中取模块、表及字段信息组装 SQL
            if (completeResp.getModuleHeaders() != null) {
                for (ModuleTableHeaderDTO h : completeResp.getModuleHeaders()) {
                    String tName = h.getTable();
                    String cName = h.getField();
                    if (tName != null
                            && cName != null
                            && validJoinedTables.contains(tName.toLowerCase())) {
                        String rawAlias = cName.toLowerCase();
                        if (addedAliases.add(rawAlias)) {
                            fields.add(DSL.field(DSL.name(tName, cName)).as(cName));
                        }
                        String qualifiedAlias = (tName + "__" + cName).toLowerCase();
                        if (addedAliases.add(qualifiedAlias)) {
                            fields.add(DSL.field(DSL.name(tName, cName)).as(tName + "__" + cName));
                        }
                    }
                }
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
