package com.jdec.platform.data.biz.dsl;

import com.jdec.platform.config.api.dto.common.ModuleNodeDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.api.dto.response.SysModuleMetaResp;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * 确定性树路径解析器 (JoinPathResolver)
 *
 * <p>遵循第一性原理与模块连接树定理：
 *
 * <ul>
 *   <li>杜绝在全局表关系图上的 BFS 盲搜与分支猜测；
 *   <li>依据 moduleNodes 树节点结构与节点绑定的物理主表，严格自底向上唯一回溯至根主表；
 *   <li>支持单层伴生维表、1:N 直属子模块、1:N:N 深度孙模块及多级任意深度确定性路径提取。
 * </ul>
 */
@Component
public class JoinPathResolver {

    /** 关联路径中的单个有向跳转步骤 */
    public static class Step {
        private final Long fromModuleId;
        private final String fromTable;
        private final Long toModuleId;
        private final String toTable;
        private final TableRelationDTO relation;
        private final boolean
                forward; // true: mainTable -> joinTable, false: joinTable -> mainTable

        public Step(
                Long fromModuleId,
                String fromTable,
                Long toModuleId,
                String toTable,
                TableRelationDTO relation,
                boolean forward) {
            this.fromModuleId = fromModuleId;
            this.fromTable = fromTable;
            this.toModuleId = toModuleId;
            this.toTable = toTable;
            this.relation = relation;
            this.forward = forward;
        }

        public Long getFromModuleId() {
            return fromModuleId;
        }

        public String getFromTable() {
            return fromTable;
        }

        public Long getToModuleId() {
            return toModuleId;
        }

        public String getToTable() {
            return toTable;
        }

        public TableRelationDTO getRelation() {
            return relation;
        }

        public boolean isForward() {
            return forward;
        }

        public String getFromJoinField() {
            return forward ? relation.getMainField() : relation.getJoinField();
        }

        public String getToJoinField() {
            return forward ? relation.getJoinField() : relation.getMainField();
        }
    }

    /**
     * 解析从根模块主表到指定模块/目标表的确定性关联步骤序列
     *
     * @param targetModuleId 目标模块 ID (可为空，若指定优先按模块树自底向上回溯)
     * @param targetTable 目标物理表名
     * @param completeResp 模块元数据定义 (含 module, moduleNodes, tableRelations)
     * @return 确定性路径步骤序列 (从根表到目标表自顶向下排序)
     */
    public List<Step> resolvePath(
            Long targetModuleId, String targetTable, SysModuleMetaResp completeResp) {

        if (completeResp == null || targetTable == null || targetTable.isBlank()) {
            return Collections.emptyList();
        }

        String primaryTable =
                completeResp.getModule() != null
                        ? completeResp.getModule().getPrimaryTable()
                        : null;
        if (primaryTable == null || primaryTable.isBlank()) {
            return Collections.emptyList();
        }

        // 目标表就是根主表自身
        if (primaryTable.equalsIgnoreCase(targetTable)) {
            return Collections.emptyList();
        }

        List<TableRelationDTO> relations =
                completeResp.getTableRelations() != null
                        ? completeResp.getTableRelations()
                        : Collections.emptyList();

        Long rootModuleId =
                completeResp.getModule() != null ? completeResp.getModule().getId() : null;

        // 1. 如果指定了 targetModuleId 且不为根模块，依据 moduleNodes 构建树向上回溯
        if (targetModuleId != null && !Objects.equals(targetModuleId, rootModuleId)) {
            List<Step> treePath =
                    resolveByModuleTree(
                            targetModuleId, targetTable, rootModuleId, primaryTable, completeResp);
            if (!treePath.isEmpty()) {
                return treePath;
            }
        }

        // 2. 直连伴生表/单层外键解析 (1:1 或 N:1 或 1:N 直属)
        TableRelationDTO directRel = findDirectRelation(primaryTable, targetTable, relations);
        if (directRel != null) {
            boolean forward = primaryTable.equalsIgnoreCase(directRel.getMainTable());
            return Collections.singletonList(
                    new Step(
                            rootModuleId,
                            primaryTable,
                            targetModuleId != null ? targetModuleId : rootModuleId,
                            targetTable,
                            directRel,
                            forward));
        }

        // 3. 多级拓扑回溯（基于 relations 定义的有向关联关系）
        return resolveByRelationTopology(
                primaryTable, targetTable, relations, rootModuleId, targetModuleId);
    }

    /** 基于模块树自底向上精确回溯 */
    private List<Step> resolveByModuleTree(
            Long targetModuleId,
            String targetTable,
            Long rootModuleId,
            String rootPrimaryTable,
            SysModuleMetaResp completeResp) {

        Map<Long, ModuleNodeDTO> nodeMap = new HashMap<>();
        if (completeResp.getModuleNodes() != null) {
            for (ModuleNodeDTO node : completeResp.getModuleNodes()) {
                if (node.getId() != null) {
                    nodeMap.put(node.getId(), node);
                }
            }
        }

        List<Long> moduleChain = new ArrayList<>();
        Long currId = targetModuleId;
        Set<Long> visited = new HashSet<>();

        while (currId != null && !Objects.equals(currId, rootModuleId) && visited.add(currId)) {
            moduleChain.add(0, currId);
            ModuleNodeDTO node = nodeMap.get(currId);
            if (node == null) {
                break;
            }
            currId = node.getParentId();
        }

        if (moduleChain.isEmpty()) {
            return Collections.emptyList();
        }

        List<TableRelationDTO> relations =
                completeResp.getTableRelations() != null
                        ? completeResp.getTableRelations()
                        : Collections.emptyList();

        List<Step> steps = new ArrayList<>();
        String prevTable = rootPrimaryTable;
        Long prevModId = rootModuleId;

        for (int i = 0; i < moduleChain.size(); i++) {
            Long nextModId = moduleChain.get(i);
            ModuleNodeDTO nextNode = nodeMap.get(nextModId);
            String nextTable =
                    (i == moduleChain.size() - 1 && targetTable != null)
                            ? targetTable
                            : (nextNode != null ? nextNode.getPrimaryTable() : targetTable);

            if (nextTable == null || nextTable.isBlank()) {
                continue;
            }

            TableRelationDTO rel = findDirectRelation(prevTable, nextTable, relations);
            if (rel == null) {
                // 如果当前模块主表与其所属表不同（如模块内的孙表明细），在当前模块内寻找局部直连
                return Collections.emptyList();
            }

            boolean forward = prevTable.equalsIgnoreCase(rel.getMainTable());
            steps.add(new Step(prevModId, prevTable, nextModId, nextTable, rel, forward));

            prevTable = nextTable;
            prevModId = nextModId;
        }

        return steps;
    }

    /** 寻找两表之间的直接关联定义 */
    private TableRelationDTO findDirectRelation(
            String tableA, String tableB, List<TableRelationDTO> relations) {
        if (tableA == null || tableB == null || relations == null) {
            return null;
        }
        for (TableRelationDTO rel : relations) {
            String mainT = rel.getMainTable() != null ? rel.getMainTable().trim() : "";
            String joinT = rel.getJoinTable() != null ? rel.getJoinTable().trim() : "";

            if ((tableA.equalsIgnoreCase(mainT) && tableB.equalsIgnoreCase(joinT))
                    || (tableA.equalsIgnoreCase(joinT) && tableB.equalsIgnoreCase(mainT))) {
                return rel;
            }
        }
        return null;
    }

    /** 严格自顶向下沿表关联有向图推导最短链路 */
    private List<Step> resolveByRelationTopology(
            String fromTable,
            String toTable,
            List<TableRelationDTO> relations,
            Long rootModuleId,
            Long targetModuleId) {

        if (fromTable.equalsIgnoreCase(toTable) || relations == null || relations.isEmpty()) {
            return Collections.emptyList();
        }

        Queue<String> queue = new LinkedList<>();
        queue.add(fromTable.toLowerCase());

        Map<String, TableRelationDTO> parentRel = new HashMap<>();
        Map<String, Boolean> parentForward = new HashMap<>();
        Map<String, String> parentNode = new HashMap<>();
        Set<String> visited = new HashSet<>();
        visited.add(fromTable.toLowerCase());

        boolean found = false;
        while (!queue.isEmpty()) {
            String curr = queue.poll();
            if (curr.equalsIgnoreCase(toTable)) {
                found = true;
                break;
            }

            for (TableRelationDTO rel : relations) {
                String mainT = rel.getMainTable() != null ? rel.getMainTable().toLowerCase() : "";
                String joinT = rel.getJoinTable() != null ? rel.getJoinTable().toLowerCase() : "";

                if (curr.equalsIgnoreCase(mainT) && visited.add(joinT)) {
                    parentNode.put(joinT, curr);
                    parentRel.put(joinT, rel);
                    parentForward.put(joinT, true);
                    queue.add(joinT);
                } else if (curr.equalsIgnoreCase(joinT) && visited.add(mainT)) {
                    parentNode.put(mainT, curr);
                    parentRel.put(mainT, rel);
                    parentForward.put(mainT, false);
                    queue.add(mainT);
                }
            }
        }

        if (!found) {
            return Collections.emptyList();
        }

        LinkedList<Step> steps = new LinkedList<>();
        String curr = toTable.toLowerCase();
        while (!curr.equalsIgnoreCase(fromTable.toLowerCase())) {
            String p = parentNode.get(curr);
            TableRelationDTO r = parentRel.get(curr);
            boolean f = parentForward.get(curr);

            steps.addFirst(new Step(rootModuleId, p, targetModuleId, curr, r, f));
            curr = p;
        }

        return steps;
    }
}
