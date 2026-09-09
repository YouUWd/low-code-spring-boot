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

            // 1. 分离子计划中的【同模块 1:N 从表】与【独立子模块】
            List<QueryNodePlan> sameModuleSubTablePlans = new ArrayList<>();
            List<QueryNodePlan> subModulePlans = new ArrayList<>();
            if (childPlan.getChildren() != null && !childPlan.getChildren().isEmpty()) {
                for (QueryNodePlan cp : childPlan.getChildren()) {
                    if (cp.getModuleId() != null && cp.getModuleId().equals(childModId)) {
                        sameModuleSubTablePlans.add(cp);
                    } else {
                        subModulePlans.add(cp);
                    }
                }
            }

            // 2. 将同模块 1:N 从表装配进当前从表行内 (例如 103 的考核分项 装配进 选课记录 行内)
            if (!sameModuleSubTablePlans.isEmpty()) {
                assembleSameModuleSubTablesIntoRows(
                        childRows, sameModuleSubTablePlans, rawDataByNode);
            }

            // 3. 检查用户请求的字段中是否包含外键字段
            boolean requestIncludesFk = isFieldExplicitlyRequested(childPlan, fkField);

            // 4. 按照外键进行 Hash 分组
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

            // 5. 将分组后的从表对象列表严格挂载在父模块的命名空间内部: record[parentModuleId][childModuleId][childTable] =
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

                    // 6. 🌟 递归装配独立子模块到当前 childModSpace 命名空间下 (例如 106 模块挂在 104 模块空间下，绝不嵌套在
                    // student_award 实体内部)
                    if (!subModulePlans.isEmpty() && !matchedChildren.isEmpty()) {
                        assembleSubModulesIntoModuleSpace(
                                childModSpace,
                                childModId,
                                matchedChildren,
                                subModulePlans,
                                rawDataByNode);
                    }
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

    /**
     * 将独立子模块装配到父模块命名空间下 (例如 将 106 挂在 record[101][104][106][student_award_detail] 下，绝不嵌套在
     * student_award 实体内部)
     */
    private void assembleSubModulesIntoModuleSpace(
            Map<String, Object> parentModSpace,
            Long parentModuleId,
            List<Map<String, Object>> parentEntities,
            List<QueryNodePlan> subModulePlans,
            Map<QueryNodePlan, List<Map<String, Object>>> rawDataByNode) {

        if (parentModSpace == null
                || parentEntities == null
                || parentEntities.isEmpty()
                || subModulePlans == null) {
            return;
        }

        // 收集属于当前父模块实体的全部主键 ID (例如 当前学生的所有 student_award 的 id)
        Set<Long> parentEntityIds = new HashSet<>();
        for (Map<String, Object> entity : parentEntities) {
            Object idVal = entity.get("_row_id");
            if (idVal == null) {
                idVal = entity.get("id");
            }
            if (idVal instanceof Number num) {
                parentEntityIds.add(num.longValue());
            }
        }

        if (parentEntityIds.isEmpty()) {
            return;
        }

        for (QueryNodePlan subPlan : subModulePlans) {
            Long subModId = subPlan.getModuleId();
            String subTable = subPlan.getPrimaryTable();
            String fkField = subPlan.getParentForeignKey();
            List<Map<String, Object>> rawRows =
                    rawDataByNode.getOrDefault(subPlan, Collections.emptyList());

            boolean requestIncludesFk = isFieldExplicitlyRequested(subPlan, fkField);

            // 筛选出属于当前父实体的子模块数据
            List<Map<String, Object>> matchedSubRows = new ArrayList<>();
            for (Map<String, Object> srow : rawRows) {
                Object fkVal = srow.get("_fk_" + fkField);
                if (fkVal == null) {
                    fkVal = srow.get(fkField);
                }
                if (fkVal instanceof Number num && parentEntityIds.contains(num.longValue())) {
                    Map<String, Object> cleanRow = new LinkedHashMap<>(srow);
                    cleanRow.remove("_row_id");
                    cleanRow.remove("_fk_" + fkField);
                    if (!requestIncludesFk && fkField != null) {
                        cleanRow.remove(fkField);
                    }
                    matchedSubRows.add(cleanRow);
                }
            }

            // 挂载到父模块空间下的独立子模块空间: parentModSpace[subModId][subTable] = matchedSubRows
            Map<String, Object> subModSpace = getOrCreateModuleSpace(parentModSpace, subModId);
            subModSpace.put(subTable, matchedSubRows);

            // 若该子模块自身还有更深层子节点，递归向下处理
            if (subPlan.getChildren() != null
                    && !subPlan.getChildren().isEmpty()
                    && !matchedSubRows.isEmpty()) {
                List<QueryNodePlan> nextSubModules = new ArrayList<>();
                List<QueryNodePlan> nextSameModules = new ArrayList<>();
                for (QueryNodePlan cp : subPlan.getChildren()) {
                    if (cp.getModuleId() != null && cp.getModuleId().equals(subModId)) {
                        nextSameModules.add(cp);
                    } else {
                        nextSubModules.add(cp);
                    }
                }
                if (!nextSameModules.isEmpty()) {
                    assembleSameModuleSubTablesIntoRows(
                            matchedSubRows, nextSameModules, rawDataByNode);
                }
                if (!nextSubModules.isEmpty()) {
                    assembleSubModulesIntoModuleSpace(
                            subModSpace, subModId, matchedSubRows, nextSubModules, rawDataByNode);
                }
            }
        }
    }

    /** 将同一模块内未独立成模块的 1:N 从表直接挂载在父从表对象内部 (例如 student_course.student_course_score_item = [...]) */
    private void assembleSameModuleSubTablesIntoRows(
            List<Map<String, Object>> parentChildRows,
            List<QueryNodePlan> sameModuleSubTablePlans,
            Map<QueryNodePlan, List<Map<String, Object>>> rawDataByNode) {

        if (parentChildRows == null
                || parentChildRows.isEmpty()
                || sameModuleSubTablePlans == null) {
            return;
        }

        for (QueryNodePlan subPlan : sameModuleSubTablePlans) {
            String subTable = subPlan.getPrimaryTable();
            String fkField = subPlan.getParentForeignKey();
            List<Map<String, Object>> subRows =
                    rawDataByNode.getOrDefault(subPlan, Collections.emptyList());

            boolean requestIncludesFk = isFieldExplicitlyRequested(subPlan, fkField);

            Map<Long, List<Map<String, Object>>> groupedByFk = new HashMap<>();
            for (Map<String, Object> srow : subRows) {
                Object fkVal = srow.get("_fk_" + fkField);
                if (fkVal == null) {
                    fkVal = srow.get(fkField);
                }
                if (fkVal instanceof Number num) {
                    Map<String, Object> cleanRow = new LinkedHashMap<>(srow);
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
                    parentRow.put(subTable, matchedList);
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
