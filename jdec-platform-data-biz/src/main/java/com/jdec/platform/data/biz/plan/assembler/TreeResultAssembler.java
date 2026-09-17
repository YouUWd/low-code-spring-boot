package com.jdec.platform.data.biz.plan.assembler;

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

    /**
     * 装配虚拟模块逻辑容器
     *
     * <p>【核心架构公理】： 1. 虚拟模块自身没有对应的物理主表，它在业务模型上充当“纯逻辑分类/业务包装容器”（如：根模块1001下的1007【财务信息】容器）； 2.
     * 虚拟模块必须保持纯粹的同构穿透性：当挂载其下的物理子模块（如1008银行卡、1009电子账户）被查询时，
     * 数据应当挂载到虚拟模块内部：parentRow[virtualModuleId][childModuleId] = [childRows]； 3.
     * 【踩坑重点】：targetContainers 与 nextContainers 必须严格使用 {@link IdentityHashMap}！ 因为 Map 在作为 Key
     * 时，如果使用普通 HashMap，当其内部挂载了子实体（Map内容被修改）后， Map.hashCode() 与 equals()
     * 会随之发生改变，导致后续兄弟子模块查找父容器时哈希错位返回 null。 使用基于内存引用的 IdentityHashMap
     * 才能绝对保证在数据行内容动态装配过程中查找容器的稳定性与可靠性。
     */
    private void assembleVirtualPlan(
            List<Map<String, Object>> parentRecords,
            QueryNodePlan virtualPlan,
            Map<QueryNodePlan, List<Map<String, Object>>> rawDataByNode) {
        Map<Map<String, Object>, Map<String, Object>> targetContainers = new IdentityHashMap<>();
        for (Map<String, Object> parentRow : parentRecords) {
            targetContainers.put(parentRow, parentRow);
        }
        assembleVirtualPlanInternal(parentRecords, targetContainers, virtualPlan, rawDataByNode);
    }

    private void assembleVirtualPlanInternal(
            List<Map<String, Object>> parentRecords,
            Map<Map<String, Object>, Map<String, Object>> targetContainers,
            QueryNodePlan virtualPlan,
            Map<QueryNodePlan, List<Map<String, Object>>> rawDataByNode) {

        String virtualKey = String.valueOf(virtualPlan.getModuleId());
        log.debug(
                "[TreeResultAssembler] 开始装配虚拟模块逻辑容器: virtualModuleId={}, 目标父行数={}, 下级子计划数={}",
                virtualKey,
                parentRecords.size(),
                virtualPlan.getChildren() != null ? virtualPlan.getChildren().size() : 0);

        // 🌟 必须使用 IdentityHashMap 建立父行引用与当前虚拟容器的映射关系，杜绝父行内容变更导致哈希寻址失效
        Map<Map<String, Object>, Map<String, Object>> nextContainers = new IdentityHashMap<>();
        for (Map<String, Object> parentRow : parentRecords) {
            Map<String, Object> parentContainer = targetContainers.get(parentRow);
            Map<String, Object> thisVirtualContainer = new LinkedHashMap<>();
            if (parentContainer != null) {
                parentContainer.put(virtualKey, thisVirtualContainer);
            } else {
                log.warn(
                        "[TreeResultAssembler] 未能在 targetContainers 中定位到父行引用，虚拟容器无法挂载: virtualKey={}, parentRowId={}",
                        virtualKey,
                        extractRowPrimaryId(parentRow));
            }
            nextContainers.put(parentRow, thisVirtualContainer);
        }

        List<QueryNodePlan> subPlans = virtualPlan.getChildren();
        if (subPlans == null || subPlans.isEmpty()) {
            log.debug("[TreeResultAssembler] 虚拟模块 [{}] 的 subPlans 为空，跳过下级子节点装配", virtualKey);
            return;
        }

        for (QueryNodePlan subPlan : subPlans) {
            log.debug(
                    "[TreeResultAssembler] 递归向下装配虚拟模块 [{}] 的子节点: childModuleId={}, primaryTable={}, parentFk={}",
                    virtualKey,
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
     * <p>【核心设计】： 1. 内存 Hash Grouping：通过父表主键与子表外键建立 O(1) 的分组匹配，杜绝在嵌套循环中全量过滤； 2.
     * 清洗技术字段：子表记录中的临时外键键值（_fk_xxx）和行号标记（_row_id）在内存装配阶段彻底剥离，对外绝不泄露内部技术字段； 3. 读写同构契约：1:1 关系挂载单个实体
     * Map（无则为 null），1:N 关系挂载实体列表 List<Map>； 4.
     * 递归自相似：若该物理子模块下仍有孙模块（如考核分项挂在选课下），以当前匹配出的子实体列表为父集，递归向下装配孙级实体。
     *
     * @param parentRecords 当前层记录集合 (根主表记录列表或上级实体列表)
     * @param containerResolver 当前父行与目标装配容器的解析函数 (直属挂载时为 identity，虚拟模块嵌套时为获取虚拟逻辑容器)
     * @param childPlan 当前物理子模块节点计划
     * @param rawDataByNode 按 QueryNodePlan 索引的各节点原始数据 Map
     */
    private void assemblePhysicalChild(
            List<Map<String, Object>> parentRecords,
            Function<Map<String, Object>, Map<String, Object>> containerResolver,
            QueryNodePlan childPlan,
            Map<QueryNodePlan, List<Map<String, Object>>> rawDataByNode) {

        Long childModId = childPlan.getModuleId();
        String fkField = childPlan.getParentForeignKey();
        List<Map<String, Object>> rawChildRows =
                rawDataByNode.getOrDefault(childPlan, Collections.emptyList());

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

        // 4. 将分组后的子模块实体直接挂载在目标容器的 [childModuleId] 键下 (1:1 挂载单对象 Map，1:N 挂载 List)
        String childModKey = String.valueOf(childModId);
        boolean isOneToOne = "1:1".equalsIgnoreCase(childPlan.getParentRelationType());

        for (Map<String, Object> parentRow : parentRecords) {
            Map<String, Object> targetContainer = containerResolver.apply(parentRow);
            if (targetContainer == null) {
                log.warn(
                        "[TreeResultAssembler] 无法获取父行对应的目标容器(targetContainer=null)，子模块挂载失败: childModuleId={}",
                        childModKey);
                continue;
            }
            Object pkVal = extractRowPrimaryId(parentRow);
            if (pkVal instanceof Number pkNum) {
                List<Map<String, Object>> matchedChildren =
                        groupedByFk.getOrDefault(pkNum.longValue(), Collections.emptyList());

                log.debug(
                        "[TreeResultAssembler] 父行匹配装配: parentPkVal={}, childModuleKey={}, 匹配到子实体行数={}",
                        pkNum.longValue(),
                        childModKey,
                        matchedChildren.size());

                if (isOneToOne) {
                    Map<String, Object> singleChild =
                            matchedChildren.isEmpty() ? null : matchedChildren.get(0);
                    targetContainer.put(childModKey, singleChild);
                } else {
                    targetContainer.put(childModKey, matchedChildren);
                }

                // 5. 🌟 递归自相似：以当前匹配的子实体列表为父级，直接向下装配孙模块（如 106 挂在 104 实体行内）
                if (!subModulePlans.isEmpty() && !matchedChildren.isEmpty()) {
                    assembleChildrenRecursively(
                            matchedChildren, childModId, subModulePlans, rawDataByNode);
                }
            } else {
                log.warn(
                        "[TreeResultAssembler] 父行主键 pkVal 非数值或为空，置空挂载: pkVal={}, childModuleKey={}",
                        pkVal,
                        childModKey);
                targetContainer.put(childModKey, isOneToOne ? null : Collections.emptyList());
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

    /**
     * 将同一模块内未独立成模块的 1:N 从表直接挂载在父从表对象内部
     *
     * <p>【业务场景】： 当同一个模块内配置了 1:N 物理从表（例如模块未拆分，但主表包含多个子表行记录时）， 从表记录直接以物理表名挂载在所属从表行内：row[subTable] =
     * [cleanRow1, cleanRow2, ...]。
     */
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

            log.debug(
                    "[TreeResultAssembler] 装配同模块 1:N 从表: subTable={}, fkField={}, rawRowsCount={}",
                    subTable,
                    fkField,
                    subRows.size());

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
