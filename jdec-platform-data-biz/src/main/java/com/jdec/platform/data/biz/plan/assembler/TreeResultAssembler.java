package com.jdec.platform.data.biz.plan.assembler;

import com.jdec.platform.data.biz.plan.model.QueryNodePlan;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * 模块树聚合根装配器 (TreeResultAssembler)
 *
 * <p>职责：将物理批处理拉取出来的子孙模块记录，根据 parentForeignKey 映射关系， 在内存中精准缝合装配为纯粹的同构实体树： parentRow[childModuleId] =
 * [ childRow1, childRow2, ... ]。 杜绝任何内部技术字段泄露（如 _row_id、_fk_xxx）。
 */
@Component
public class TreeResultAssembler {

    /**
     * 将各层拉取的子孙模块数据，根据各节点的 queryNodePlan 与 parentForeignKey 递归装配进父行内部
     *
     * @param parentRecords 当前层记录集合 (如根主表记录列表，或上级子模块记录列表)
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
            String fkField = childPlan.getParentForeignKey();
            List<Map<String, Object>> rawChildRows =
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

            // 2. 深度复制子行并按外键分组 (Hash Grouping)，同时彻底移除临时技术键 _fk_xxx
            Map<Long, List<Map<String, Object>>> groupedByFk = new HashMap<>();
            List<Map<String, Object>> cleanChildRows = new ArrayList<>();

            for (Map<String, Object> crow : rawChildRows) {
                Object fkVal = crow.get("_fk_" + fkField);
                if (fkVal == null) {
                    fkVal = crow.get(fkField);
                }

                Map<String, Object> cleanRow = new LinkedHashMap<>(crow);
                cleanRow.remove("_fk_" + fkField);
                cleanRow.remove("_row_id");

                if (fkVal instanceof Number num) {
                    groupedByFk
                            .computeIfAbsent(num.longValue(), k -> new ArrayList<>())
                            .add(cleanRow);
                }
                cleanChildRows.add(cleanRow);
            }

            // 3. 将同模块 1:N 从表装配进当前从表行内 (例如 选课记录 行内挂载 成绩分项)
            if (!sameModuleSubTablePlans.isEmpty()) {
                assembleSameModuleSubTablesIntoRows(
                        cleanChildRows, sameModuleSubTablePlans, rawDataByNode);
            }

            // 4. 将分组后的子模块实体数组直接挂载在父实体的 [childModuleId] 键下
            String childModKey = String.valueOf(childModId);
            for (Map<String, Object> parentRow : parentRecords) {
                Object pkVal = extractRowPrimaryId(parentRow);
                if (pkVal instanceof Number pkNum) {
                    List<Map<String, Object>> matchedChildren =
                            groupedByFk.getOrDefault(pkNum.longValue(), Collections.emptyList());
                    parentRow.put(childModKey, matchedChildren);

                    // 5. 🌟 递归自相似：以当前匹配的子实体列表为父级，直接向下装配孙模块（如 106 挂在 104 实体行内）
                    if (!subModulePlans.isEmpty() && !matchedChildren.isEmpty()) {
                        assembleChildrenRecursively(
                                matchedChildren, childModId, subModulePlans, rawDataByNode);
                    }
                } else {
                    parentRow.put(childModKey, Collections.emptyList());
                }
            }
        }
    }

    /** 提取数据行的物理主键 ID (支持从内部物理表 map 中读取 id) */
    public static Object extractRowPrimaryId(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object id = row.get("id");
        if (id != null) {
            return id;
        }
        for (Object val : row.values()) {
            if (val instanceof Map<?, ?> map) {
                Object subId = map.get("id");
                if (subId != null) {
                    return subId;
                }
            }
        }
        return null;
    }

    /** 将同一模块内未独立成模块的 1:N 从表直接挂载在父从表对象内部 */
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

            Map<Long, List<Map<String, Object>>> groupedByFk = new HashMap<>();
            for (Map<String, Object> srow : subRows) {
                Object fkVal = srow.get("_fk_" + fkField);
                if (fkVal == null) {
                    fkVal = srow.get(fkField);
                }

                // 🌟 若从表行已包含在 subTable 物理表空间内，直接提取物理列 Map，消除 subTable[0].subTable 双层同名包裹
                Map<String, Object> cleanRow;
                if (srow.get(subTable) instanceof Map<?, ?> tableMap) {
                    cleanRow = new LinkedHashMap<>((Map<String, Object>) tableMap);
                } else {
                    cleanRow = new LinkedHashMap<>(srow);
                    cleanRow.remove("_row_id");
                    cleanRow.remove("_fk_" + fkField);
                }

                if (fkVal == null) {
                    fkVal = cleanRow.get(fkField);
                }

                if (fkVal instanceof Number num) {
                    groupedByFk
                            .computeIfAbsent(num.longValue(), k -> new ArrayList<>())
                            .add(cleanRow);
                }
            }

            for (Map<String, Object> parentRow : parentChildRows) {
                Object pkVal = extractRowPrimaryId(parentRow);
                if (pkVal instanceof Number pkNum) {
                    List<Map<String, Object>> matchedList =
                            groupedByFk.getOrDefault(pkNum.longValue(), Collections.emptyList());
                    parentRow.put(subTable, matchedList);
                }
            }
        }
    }
}
