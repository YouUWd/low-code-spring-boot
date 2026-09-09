package com.jdec.platform.data.biz.plan.assembler;

import com.jdec.platform.data.biz.plan.model.PhysicalFieldSpec;
import com.jdec.platform.data.biz.plan.model.QueryNodePlan;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * 模块树聚合根装配器 (TreeResultAssembler)
 *
 * <p>职责：将物理批处理拉取出来的子孙模块记录，根据 parentForeignKey 映射关系， 在内存中精准缝合组装为模块化命名空间嵌套结构（形如
 * record[moduleId][tableName] = [...]）。
 */
@Component
public class TreeResultAssembler {

    /**
     * 将各层拉取的数据，根据各节点的 queryNodePlan 与 parentForeignKey 递归装配进主记录内部
     *
     * @param parentRecords 当前层记录集合 (如根主表记录列表)
     * @param parentModuleId 当前父节点的模块ID (如根模块101)
     * @param childPlans 下级子模块节点计划列表
     * @param rawDataByNode 按 QueryNodePlan 实例索引的原始数据 Map
     */
    public void assembleChildrenRecursively(
            List<Map<String, Object>> parentRecords,
            Long parentModuleId,
            List<QueryNodePlan> childPlans,
            Map<QueryNodePlan, List<Map<String, Object>>> rawDataByNode) {

        if (parentRecords == null
                || parentRecords.isEmpty()
                || childPlans == null
                || childPlans.isEmpty()) {
            return;
        }

        for (QueryNodePlan childPlan : childPlans) {
            Long childModId = childPlan.getModuleId();
            String childTable = childPlan.getPrimaryTable();
            String fkField = childPlan.getParentForeignKey();
            List<Map<String, Object>> childRows =
                    rawDataByNode.getOrDefault(childPlan, Collections.emptyList());

            // 1. 递归优先将更深层的孙模块/孙表装配进当前从表行内 (例如 考核分项 装配进 选课记录 行内)
            if (childPlan.getChildren() != null && !childPlan.getChildren().isEmpty()) {
                assembleGrandChildrenIntoRows(childRows, childPlan.getChildren(), rawDataByNode);
            }

            // 2. 检查用户请求的字段中是否包含外键字段
            boolean requestIncludesFk = isFieldExplicitlyRequested(childPlan, fkField);

            // 3. 按照外键进行 Hash 分组
            Map<Long, List<Map<String, Object>>> groupedByFk = new HashMap<>();
            for (Map<String, Object> crow : childRows) {
                Object fkVal = crow.get("_fk_" + fkField);
                if (fkVal == null) {
                    fkVal = crow.get(fkField);
                }
                if (fkVal instanceof Number num) {
                    Map<String, Object> cleanRow = new LinkedHashMap<>(crow);
                    // 彻底剥离内部技术键
                    cleanRow.remove("_row_id");
                    cleanRow.remove("_fk_" + fkField);
                    if (!requestIncludesFk && fkField != null) {
                        cleanRow.remove(fkField);
                    }
                    groupedByFk
                            .computeIfAbsent(num.longValue(), k -> new ArrayList<>())
                            .add(cleanRow);
                }
            }

            // 4. 将分组后的从表对象列表严格挂载在父模块的命名空间内部: record[parentModuleId][childModuleId][childTable] =
            // [...]
            for (Map<String, Object> parentRow : parentRecords) {
                Object pkVal = extractRowPrimaryId(parentRow);
                if (pkVal instanceof Number pkNum) {
                    List<Map<String, Object>> matchedChildren =
                            groupedByFk.getOrDefault(pkNum.longValue(), Collections.emptyList());

                    // 获取父模块命名空间，若无则挂在 parentRow 顶层
                    Map<String, Object> parentModSpace =
                            parentModuleId != null
                                    ? getOrCreateModuleSpace(parentRow, parentModuleId)
                                    : parentRow;

                    // 挂载子模块: parentModSpace[childModId][childTable] = [...]
                    Map<String, Object> childModSpace =
                            getOrCreateModuleSpace(parentModSpace, childModId);
                    childModSpace.put(childTable, matchedChildren);
                }
            }
        }
    }

    /** 智能提取数据行的主键 ID（优先从内部技术锚点 _row_id 读取，再从模块命名空间下的主表中提取） */
    private Object extractRowPrimaryId(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object rowId = row.get("_row_id");
        if (rowId != null) {
            return rowId;
        }
        Object id = row.get("id");
        if (id != null) {
            return id;
        }
        for (Object modObj : row.values()) {
            if (modObj instanceof Map<?, ?> modMap) {
                for (Object tblObj : modMap.values()) {
                    if (tblObj instanceof Map<?, ?> tblMap) {
                        Object tblRowId = tblMap.get("_row_id");
                        if (tblRowId != null) {
                            return tblRowId;
                        }
                        Object tblId = tblMap.get("id");
                        if (tblId != null) {
                            return tblId;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** 将孙模块/孙表数据直接挂载在父从表对象内部 (形如 student_course.student_course_score_item = [...]) */
    private void assembleGrandChildrenIntoRows(
            List<Map<String, Object>> parentChildRows,
            List<QueryNodePlan> grandChildPlans,
            Map<QueryNodePlan, List<Map<String, Object>>> rawDataByNode) {

        if (parentChildRows == null || parentChildRows.isEmpty() || grandChildPlans == null) {
            return;
        }

        for (QueryNodePlan grandChildPlan : grandChildPlans) {
            String grandTable = grandChildPlan.getPrimaryTable();
            String fkField = grandChildPlan.getParentForeignKey();
            List<Map<String, Object>> grandChildRows =
                    rawDataByNode.getOrDefault(grandChildPlan, Collections.emptyList());

            // 如果还有更深层（曾孙），继续递归
            if (grandChildPlan.getChildren() != null && !grandChildPlan.getChildren().isEmpty()) {
                assembleGrandChildrenIntoRows(
                        grandChildRows, grandChildPlan.getChildren(), rawDataByNode);
            }

            boolean requestIncludesFk = isFieldExplicitlyRequested(grandChildPlan, fkField);

            Map<Long, List<Map<String, Object>>> groupedByFk = new HashMap<>();
            for (Map<String, Object> grow : grandChildRows) {
                Object fkVal = grow.get("_fk_" + fkField);
                if (fkVal == null) {
                    fkVal = grow.get(fkField);
                }
                if (fkVal instanceof Number num) {
                    Map<String, Object> cleanRow = new LinkedHashMap<>(grow);
                    cleanRow.remove("_row_id");
                    cleanRow.remove("_fk_" + fkField);
                    if (!requestIncludesFk && fkField != null) {
                        cleanRow.remove(fkField);
                    }
                    groupedByFk
                            .computeIfAbsent(num.longValue(), k -> new ArrayList<>())
                            .add(cleanRow);
                }
            }

            for (Map<String, Object> parentRow : parentChildRows) {
                Object pkVal = parentRow.get("_row_id");
                if (pkVal == null) {
                    pkVal = parentRow.get("id");
                }
                if (pkVal instanceof Number pkNum) {
                    List<Map<String, Object>> matchedList =
                            groupedByFk.getOrDefault(pkNum.longValue(), Collections.emptyList());
                    parentRow.put(grandTable, matchedList);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateModuleSpace(Map<String, Object> record, Long moduleId) {
        String key = String.valueOf(moduleId);
        Object space = record.get(key);
        if (space instanceof Map<?, ?> mapSpace) {
            return (Map<String, Object>) mapSpace;
        }
        Map<String, Object> newSpace = new LinkedHashMap<>();
        record.put(key, newSpace);
        return newSpace;
    }

    private boolean isFieldExplicitlyRequested(QueryNodePlan plan, String columnName) {
        if (plan == null || plan.getProjectedFields() == null || columnName == null) {
            return false;
        }
        for (PhysicalFieldSpec spec : plan.getProjectedFields()) {
            if (columnName.equalsIgnoreCase(spec.getColumnName())) {
                return true;
            }
        }
        return false;
    }
}
