package com.jdec.platform.data.biz.plan.assembler;

import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.biz.plan.model.QueryNodePlan;
import java.util.*;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 模块树聚合根装配器 (TreeResultAssembler)
 *
 * <p>职责：将物理批处理拉取出来的子孙模块记录，根据 parentForeignKey 映射关系， 在内存中精准缝合装配为纯粹的同构实体树： parentRow[childModuleId] =
 * [ childRow1, childRow2, ... ]。 杜绝任何内部技术字段泄露（如 _row_id、_fk_xxx）。
 */
@Slf4j
@Component
public class TreeResultAssembler {

    /**
     * 将各层拉取的子孙模块数据，根据各节点的 queryNodePlan 与 parentForeignKey 递归装配进父行内部 (DataRow / DataNode)
     *
     * @param parentRecords 当前层记录集合 (如根主表记录列表，或上级子模块记录列表)
     * @param parentModuleId 当前父节点的模块ID (如根模块101)
     * @param childPlans 下级子模块节点计划列表
     * @param rawDataByNode 按 QueryNodePlan 实例索引的原始数据 Map
     */
    public void assembleChildrenRecursively(
            List<DataRow> parentRecords,
            Long parentModuleId,
            List<QueryNodePlan> childPlans,
            Map<QueryNodePlan, List<DataRow>> rawDataByNode) {

        if (parentRecords == null
                || parentRecords.isEmpty()
                || childPlans == null
                || childPlans.isEmpty()) {
            return;
        }

        log.debug(
                "[TreeResultAssembler] 启动递归装配: parentModuleId={}, 父记录数={}, 下级待装配计划数={}",
                parentModuleId,
                parentRecords.size(),
                childPlans.size());

        for (QueryNodePlan childPlan : childPlans) {
            // 区分虚拟模块容器装配与物理子表装配
            if (childPlan.getPrimaryTable() == null || childPlan.getPrimaryTable().isBlank()) {
                assembleVirtualPlan(parentRecords, childPlan, rawDataByNode);
                continue;
            }
            assemblePhysicalChild(parentRecords, Function.identity(), childPlan, rawDataByNode);
        }
    }

    /** 装配虚拟模块逻辑容器 */
    private void assembleVirtualPlan(
            List<DataRow> parentRecords,
            QueryNodePlan virtualPlan,
            Map<QueryNodePlan, List<DataRow>> rawDataByNode) {
        Map<DataRow, DataRow> targetContainers = new IdentityHashMap<>();
        for (DataRow parentRow : parentRecords) {
            targetContainers.put(parentRow, parentRow);
        }
        assembleVirtualPlanInternal(parentRecords, targetContainers, virtualPlan, rawDataByNode);
    }

    private void assembleVirtualPlanInternal(
            List<DataRow> parentRecords,
            Map<DataRow, DataRow> targetContainers,
            QueryNodePlan virtualPlan,
            Map<QueryNodePlan, List<DataRow>> rawDataByNode) {

        Long virtualModuleId = virtualPlan.getModuleId();
        log.debug(
                "[TreeResultAssembler] 开始装配虚拟模块逻辑容器: virtualModuleId={}, 目标父行数={}, 下级子计划数={}",
                virtualModuleId,
                parentRecords.size(),
                virtualPlan.getChildren() != null ? virtualPlan.getChildren().size() : 0);

        // 🌟 必须使用 IdentityHashMap 建立父行引用与当前虚拟容器的映射关系，杜绝父行内容变更导致哈希寻址失效
        Map<DataRow, DataRow> nextContainers = new IdentityHashMap<>();
        for (DataRow parentRow : parentRecords) {
            DataRow parentContainer = targetContainers.get(parentRow);
            DataRow thisVirtualContainer = new DataRow();
            if (parentContainer != null) {
                if (parentContainer.getChildren() == null) {
                    parentContainer.setChildren(new ArrayList<>());
                }
                DataBlock virtualNode =
                        DataBlock.builder()
                                .moduleId(virtualModuleId)
                                .records(Collections.singletonList(thisVirtualContainer))
                                .build();
                parentContainer.getChildren().add(virtualNode);
            } else {
                log.warn(
                        "[TreeResultAssembler] 未能在 targetContainers 中定位到父行引用，虚拟容器无法挂载: virtualModuleId={}, parentRowId={}",
                        virtualModuleId,
                        extractRowPrimaryId(parentRow));
            }
            nextContainers.put(parentRow, thisVirtualContainer);
        }

        List<QueryNodePlan> subPlans = virtualPlan.getChildren();
        if (subPlans == null || subPlans.isEmpty()) {
            log.debug("[TreeResultAssembler] 虚拟模块 [{}] 的 subPlans 为空，跳过下级子节点装配", virtualModuleId);
            return;
        }

        for (QueryNodePlan subPlan : subPlans) {
            log.debug(
                    "[TreeResultAssembler] 递归向下装配虚拟模块 [{}] 的子节点: childModuleId={}, primaryTable={}, parentFk={}",
                    virtualModuleId,
                    subPlan.getModuleId(),
                    subPlan.getPrimaryTable(),
                    subPlan.getParentForeignKey());
            if (subPlan.getPrimaryTable() == null || subPlan.getPrimaryTable().isBlank()) {
                // 子节点若依然是嵌套的虚拟模块，继续递归钻取
                assembleVirtualPlanInternal(parentRecords, nextContainers, subPlan, rawDataByNode);
            } else {
                // 物理子模块直接挂载进对应的虚拟容器
                assemblePhysicalChild(parentRecords, nextContainers::get, subPlan, rawDataByNode);
            }
        }
    }

    /**
     * 装配物理从表/子模块
     *
     * @param parentRecords 当前层记录集合 (根主表记录列表或上级实体列表)
     * @param containerResolver 当前父行与目标装配容器的解析函数
     * @param childPlan 当前物理子模块节点计划
     * @param rawDataByNode 按 QueryNodePlan 索引的各节点原始数据 Map
     */
    private void assemblePhysicalChild(
            List<DataRow> parentRecords,
            Function<DataRow, DataRow> containerResolver,
            QueryNodePlan childPlan,
            Map<QueryNodePlan, List<DataRow>> rawDataByNode) {

        Long childModId = childPlan.getModuleId();
        String fkField = childPlan.getParentForeignKey();
        List<DataRow> rawChildRows = rawDataByNode.getOrDefault(childPlan, Collections.emptyList());

        log.debug(
                "[TreeResultAssembler] 物理子模块装配开始: moduleId={}, primaryTable={}, parentFk={}, rawRowsCount={}",
                childModId,
                childPlan.getPrimaryTable(),
                fkField,
                rawChildRows.size());

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
        Map<Long, List<DataRow>> groupedByFk = new HashMap<>();
        List<DataRow> cleanChildRows = new ArrayList<>();

        for (DataRow crow : rawChildRows) {
            Object fkVal = null;
            if (crow.getTables() != null) {
                fkVal = crow.getTables().get("_fk_" + fkField);
                if (fkVal == null) {
                    fkVal = crow.getTables().get(fkField);
                }
            }

            DataRow cleanRow = new DataRow();
            if (crow.getTables() != null) {
                cleanRow.getTables().putAll(crow.getTables());
                cleanRow.getTables().remove("_fk_" + fkField);
                cleanRow.getTables().remove("_row_id");
            }
            if (crow.getChildren() != null) {
                cleanRow.setChildren(new ArrayList<>(crow.getChildren()));
            }

            if (fkVal instanceof Number num) {
                groupedByFk.computeIfAbsent(num.longValue(), k -> new ArrayList<>()).add(cleanRow);
            } else {
                log.warn(
                        "[TreeResultAssembler] 物理子行 fkVal 非数字: fkField={}, fkVal={}",
                        fkField,
                        fkVal);
            }
            cleanChildRows.add(cleanRow);
        }

        log.debug(
                "[TreeResultAssembler] 物理子模块 [{}] 分组完毕: 匹配键集(外键值)={}",
                childModId,
                groupedByFk.keySet());

        // 3. 将同模块 1:N 从表装配进当前从表行内 (例如 选课记录 行内挂载 成绩分项)
        if (!sameModuleSubTablePlans.isEmpty()) {
            assembleSameModuleSubTablesIntoRows(
                    cleanChildRows, sameModuleSubTablePlans, rawDataByNode);
        }

        // 4. 将分组后的子模块实体挂载在目标容器的 children 列表中 (自相似读写同构)
        for (DataRow parentRow : parentRecords) {
            DataRow targetContainer = containerResolver.apply(parentRow);
            if (targetContainer == null) {
                log.warn(
                        "[TreeResultAssembler] 无法获取父行对应的目标容器(targetContainer=null)，子模块挂载失败: childModuleId={}",
                        childModId);
                continue;
            }
            Object pkVal = extractRowPrimaryId(parentRow);
            if (targetContainer.getChildren() == null) {
                targetContainer.setChildren(new ArrayList<>());
            }

            if (pkVal instanceof Number pkNum) {
                List<DataRow> matchedChildren =
                        groupedByFk.getOrDefault(pkNum.longValue(), Collections.emptyList());

                log.debug(
                        "[TreeResultAssembler] 父行匹配装配: parentPkVal={}, childModuleId={}, 匹配到子实体行数={}",
                        pkNum.longValue(),
                        childModId,
                        matchedChildren.size());

                DataBlock childModuleNode =
                        DataBlock.builder().moduleId(childModId).records(matchedChildren).build();
                targetContainer.getChildren().add(childModuleNode);

                // 5. 🌟 递归自相似：以当前匹配的子实体列表为父级，直接向下装配孙模块
                if (!subModulePlans.isEmpty() && !matchedChildren.isEmpty()) {
                    assembleChildrenRecursively(
                            matchedChildren, childModId, subModulePlans, rawDataByNode);
                }
            } else {
                log.warn(
                        "[TreeResultAssembler] 父行主键 pkVal 非数值或为空，置空挂载: pkVal={}, childModuleId={}",
                        pkVal,
                        childModId);
                DataBlock childModuleNode =
                        DataBlock.builder()
                                .moduleId(childModId)
                                .records(Collections.emptyList())
                                .build();
                targetContainer.getChildren().add(childModuleNode);
            }
        }
    }

    /** 提取数据行的物理主键 ID (支持从内部物理表 map 中读取 id) */
    public static Object extractRowPrimaryId(DataRow row) {
        if (row == null || row.getTables() == null) {
            return null;
        }
        Object id = row.getTables().get("id");
        if (id != null) {
            return id;
        }
        for (Object val : row.getTables().values()) {
            if (val instanceof Map<?, ?> map) {
                Object subId = map.get("id");
                if (subId != null) {
                    return subId;
                }
            }
        }
        return null;
    }

    /**
     * 将同一模块内未独立成模块的 1:N 从表直接挂载在父从表对象内部
     *
     * <p>【业务场景】： 当同一个模块内配置了 1:N 物理从表（例如模块未拆分，但主表包含多个子表行记录时）， 从表记录直接以物理表名挂载在所属从表行内：row[subTable] =
     * [cleanRow1, cleanRow2, ...]。
     */
    private void assembleSameModuleSubTablesIntoRows(
            List<DataRow> parentChildRows,
            List<QueryNodePlan> sameModuleSubTablePlans,
            Map<QueryNodePlan, List<DataRow>> rawDataByNode) {

        if (parentChildRows == null
                || parentChildRows.isEmpty()
                || sameModuleSubTablePlans == null) {
            return;
        }

        for (QueryNodePlan subPlan : sameModuleSubTablePlans) {
            String subTable = subPlan.getPrimaryTable();
            String fkField = subPlan.getParentForeignKey();
            List<DataRow> subRows = rawDataByNode.getOrDefault(subPlan, Collections.emptyList());

            log.debug(
                    "[TreeResultAssembler] 装配同模块 1:N 从表: subTable={}, fkField={}, rawRowsCount={}",
                    subTable,
                    fkField,
                    subRows.size());

            Map<Long, List<Map<String, Object>>> groupedByFk = new HashMap<>();
            for (DataRow srow : subRows) {
                Object fkVal = null;
                if (srow.getTables() != null) {
                    fkVal = srow.getTables().get("_fk_" + fkField);
                    if (fkVal == null) {
                        fkVal = srow.getTables().get(fkField);
                    }
                }

                // 🌟 若从表行已包含在 subTable 物理表空间内，直接提取物理列 Map，消除 subTable[0].subTable 双层同名包裹
                Map<String, Object> cleanRow;
                if (srow.getTable(subTable) != null) {
                    cleanRow = new LinkedHashMap<>(srow.getTable(subTable));
                } else if (srow.getTables() != null) {
                    cleanRow = new LinkedHashMap<>(srow.getTables());
                    cleanRow.remove("_row_id");
                    cleanRow.remove("_fk_" + fkField);
                } else {
                    cleanRow = new LinkedHashMap<>();
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

            for (DataRow parentRow : parentChildRows) {
                Object pkVal = extractRowPrimaryId(parentRow);
                if (pkVal instanceof Number pkNum) {
                    List<Map<String, Object>> matchedList =
                            groupedByFk.getOrDefault(pkNum.longValue(), Collections.emptyList());
                    if (parentRow.getTables() != null) {
                        parentRow.getTables().put(subTable, matchedList);
                    }
                    log.debug(
                            "[TreeResultAssembler] 同模块从表挂载完成: parentPkVal={}, subTable={}, 挂载行数={}",
                            pkNum.longValue(),
                            subTable,
                            matchedList.size());
                }
            }
        }
    }
}
