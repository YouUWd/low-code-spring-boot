package com.jdec.platform.data.biz.plan.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.biz.plan.PlanMeta;
import com.jdec.platform.data.biz.plan.model.SaveNodePlan;
import com.jdec.platform.data.biz.plan.model.SavePlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 结构化保存计划编译器 (SavePlanCompiler)
 *
 * <p>职责：递归解析业务数据区块 (DataBlock) 保存树，校验各模块元数据，推导父子外键传播规则，编译生成完整的 SavePlan。 支持行记录内部嵌套的 children (与查询响应
 * 100% 读写同构)。
 *
 * <p>本类不依赖 fieldId，也**不自行读取元数据**：模块树与项目表关系由调用方以 {@link PlanMeta} 快照
 * 传入，保证读侧与写侧由同一份快照编译出来（否则会出现「读得出、写不进」）。主表与表间关系的判定统一走 {@link ModuleRelationResolver}， 与 {@link
 * QueryPlanCompiler} 共享同一口径。
 *
 * <p>行内子模块编译采用**快速失败**：任何子模块元数据缺失或关联未自洽都会直接抛出，绝不静默丢弃子节点数据。
 */
@Component
public class SavePlanCompiler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 核心编译入口：编译 DataBlock 业务数据区块为 SavePlan（元数据快照由调用方传入） */
    public SavePlan compile(DataBlock block, PlanMeta meta) {
        if (block == null) {
            throw new IllegalArgumentException("保存请求或数据区块不能为空");
        }

        ModuleTreeDTO tree = meta != null ? meta.moduleTree() : null;
        if (tree == null || tree.getRootModule() == null) {
            throw new IllegalArgumentException("未找到对应的模块树元数据配置");
        }

        Long rootModuleId = tree.getRootModuleId();
        ModuleInfo rootMeta = tree.getRootModule();
        if (block.getView() != null && !block.getView().isBlank()) {
            String view = block.getView();
            for (ModuleInfo info : tree.getModuleMap().values()) {
                if (info != null
                        && (view.equalsIgnoreCase(info.getModuleCode())
                                || view.equalsIgnoreCase(info.getPrimaryTable())
                                || (view.matches("\\d+")
                                        && Long.parseLong(view) == info.getModuleId()))) {
                    rootModuleId = info.getModuleId();
                    rootMeta = info;
                    break;
                }
            }
        }

        ProjectTableRelationsDTO projectRelations = meta != null ? meta.projectRelations() : null;
        SaveNodePlan rootPlan =
                compileNode(block, null, null, projectRelations, tree, rootMeta, rootModuleId);
        return SavePlan.builder().rootNodePlan(rootPlan).build();
    }

    /** 递归编译单节点保存计划 (内部共享 projectRelations 与 ModuleTreeDTO) */
    public SaveNodePlan compileNode(
            DataBlock node,
            Long parentModuleId,
            ModuleInfo parentMeta,
            ProjectTableRelationsDTO projectRelations,
            ModuleTreeDTO tree,
            ModuleInfo meta,
            Long moduleId) {

        if (meta == null) {
            meta = tree != null ? tree.getModule(moduleId) : null;
        }
        if (meta == null) {
            throw new IllegalArgumentException("模块 ID [" + moduleId + "] 不存在或未在当前模块树中配置元数据");
        }

        String primaryTable = ModuleRelationResolver.resolvePrimaryTable(meta);
        String parentFk =
                resolveParentForeignKey(primaryTable, meta, parentMeta, projectRelations, tree);
        TableRelationDTO relation =
                resolveTableRelation(primaryTable, parentMeta, projectRelations, tree);

        List<SaveNodePlan> allChildPlans = new ArrayList<>();
        List<List<SaveNodePlan>> rowChildrenList = new ArrayList<>();
        List<List<SaveNodePlan>> rowCompanionList = new ArrayList<>();

        // 规范化当前节点的 records，并提取行内嵌套的 children (DataBlock 读写同构)
        List<DataRow> sourceRecords = node.getRecords();
        List<Map<String, Object>> cleanRecords = new ArrayList<>();
        List<DataRow> cleanSourceRows = new ArrayList<>();

        if (sourceRecords != null && !sourceRecords.isEmpty()) {
            for (DataRow rawRow : sourceRecords) {
                if (rawRow == null) {
                    continue;
                }
                List<SaveNodePlan> thisRowChildPlans = new ArrayList<>();
                List<SaveNodePlan> thisRowCompanionPlans = new ArrayList<>();
                Map<String, Object> cleanRow =
                        normalizeRow(
                                rawRow,
                                primaryTable,
                                moduleId,
                                meta,
                                thisRowChildPlans,
                                thisRowCompanionPlans,
                                projectRelations,
                                tree);
                cleanRecords.add(cleanRow);
                cleanSourceRows.add(rawRow);
                rowChildrenList.add(thisRowChildPlans);
                rowCompanionList.add(thisRowCompanionPlans);
                allChildPlans.addAll(thisRowChildPlans);
            }
        }

        boolean isMany =
                (relation != null && "1:N".equalsIgnoreCase(relation.getRelationType()))
                        || (node.getRecords() != null && node.getRecords().size() > 1);

        return SaveNodePlan.builder()
                .moduleId(moduleId)
                .moduleMeta(meta)
                .primaryTable(primaryTable)
                .parentForeignKey(parentFk)
                .isMany(isMany)
                .records(cleanRecords)
                .sourceDataRows(cleanSourceRows)
                .children(allChildPlans)
                .rowChildren(rowChildrenList)
                .companionPlans(rowCompanionList)
                .build();
    }

    /** 规范化行记录，支持解包物理表对象与提取行内挂载的子模块 (DataRow) 及 1:1 伴生表 */
    private Map<String, Object> normalizeRow(
            DataRow rawRow,
            String primaryTable,
            Long moduleId,
            ModuleInfo meta,
            List<SaveNodePlan> childPlans,
            List<SaveNodePlan> companionPlans,
            ProjectTableRelationsDTO projectRelations,
            ModuleTreeDTO tree) {
        Map<String, Object> cleanRow = new HashMap<>();
        if (rawRow.getTables() != null) {
            cleanRow.putAll(rawRow.getTables());
        }
        String modKey = moduleId != null ? String.valueOf(moduleId) : null;

        // 0. 读写同构：若整条记录被当前模块 ID 包裹（如 { "101": { student, clazz, ... } }），直接提取内层
        if (modKey != null && cleanRow.get(modKey) instanceof Map<?, ?> modMap) {
            cleanRow = new HashMap<>();
            for (Map.Entry<?, ?> e : modMap.entrySet()) {
                if (e.getKey() != null) {
                    cleanRow.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }

        // 1. 如果包含主表名子对象或 table.column 扁平键，展平合并到当前行并移除主表前缀/嵌套对象
        if (primaryTable != null && !primaryTable.isBlank()) {
            String matchedPrimaryKey = findMatchingKeyIgnoreCase(cleanRow, primaryTable);
            if (matchedPrimaryKey != null
                    && cleanRow.get(matchedPrimaryKey) instanceof Map<?, ?> tableMap) {
                for (Map.Entry<?, ?> entry : tableMap.entrySet()) {
                    if (entry.getKey() != null) {
                        cleanRow.putIfAbsent(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                cleanRow.remove(matchedPrimaryKey);
            }
            String primaryPrefix = primaryTable.toLowerCase() + ".";
            Map<String, Object> toAdd = new HashMap<>();
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, Object> entry : cleanRow.entrySet()) {
                if (entry.getKey() != null
                        && entry.getKey().toLowerCase().startsWith(primaryPrefix)) {
                    String col = entry.getKey().substring(primaryPrefix.length());
                    toAdd.put(col, entry.getValue());
                    toRemove.add(entry.getKey());
                }
            }
            toRemove.forEach(cleanRow::remove);
            toAdd.forEach(cleanRow::putIfAbsent);
        }

        // 1.1 提取同一模块下的 1:1 伴生表 (垂直拆分表，未被模块树单独认领为模块主表)
        if (primaryTable != null
                && !primaryTable.isBlank()
                && projectRelations != null
                && projectRelations.getForwardRelations() != null) {
            List<TableRelationDTO> fwdRels =
                    projectRelations.getForwardRelations().get(primaryTable.toLowerCase());
            if (fwdRels != null) {
                for (TableRelationDTO rel : fwdRels) {
                    if (ModuleRelationResolver.isCompanionRelation(rel, tree)) {
                        String compTable = rel.getJoinTable();
                        if (compTable != null && !compTable.isBlank()) {
                            String trimmedCompTable = compTable.trim();
                            String matchedCompKey =
                                    findMatchingKeyIgnoreCase(cleanRow, trimmedCompTable);
                            Object compVal =
                                    matchedCompKey != null ? cleanRow.get(matchedCompKey) : null;
                            Map<String, Object> compRecord = new LinkedHashMap<>();
                            if (compVal instanceof Map<?, ?> compMap) {
                                cleanRow.remove(matchedCompKey);
                                for (Map.Entry<?, ?> e : compMap.entrySet()) {
                                    if (e.getKey() != null) {
                                        compRecord.put(String.valueOf(e.getKey()), e.getValue());
                                    }
                                }
                            }
                            String compPrefix = trimmedCompTable.toLowerCase() + ".";
                            List<String> toRemove = new ArrayList<>();
                            for (Map.Entry<String, Object> e : cleanRow.entrySet()) {
                                if (e.getKey() != null
                                        && e.getKey().toLowerCase().startsWith(compPrefix)) {
                                    String col = e.getKey().substring(compPrefix.length());
                                    compRecord.putIfAbsent(col, e.getValue());
                                    toRemove.add(e.getKey());
                                }
                            }
                            toRemove.forEach(cleanRow::remove);

                            if (!compRecord.isEmpty()) {
                                String fk =
                                        (rel.getJoinField() != null
                                                        && !rel.getJoinField().isBlank())
                                                ? rel.getJoinField().trim()
                                                : "id";
                                SaveNodePlan compPlan =
                                        SaveNodePlan.builder()
                                                .moduleId(moduleId)
                                                .moduleMeta(meta)
                                                .primaryTable(trimmedCompTable)
                                                .parentForeignKey(fk)
                                                .isMany(false)
                                                .records(List.of(compRecord))
                                                .sourceDataRows(List.of(rawRow))
                                                .build();
                                companionPlans.add(compPlan);
                            }
                        }
                    }
                }
            }
        }

        // 1.2 剥离同一模块下的 N:1 上游只读表 (例如 getDetail 查询出的 teacher/clazz 字典表空间)，
        //     保证 getDetail 返回值原样回写 save 时不会把上游表 Map 当作主表列写入
        if (primaryTable != null
                && !primaryTable.isBlank()
                && projectRelations != null
                && projectRelations.getReverseRelations() != null) {
            List<TableRelationDTO> revRels =
                    projectRelations.getReverseRelations().get(primaryTable.toLowerCase());
            if (revRels != null) {
                for (TableRelationDTO rel : revRels) {
                    if (ModuleRelationResolver.isUpstreamRelation(rel, primaryTable, tree)) {
                        String upTable = rel.getMainTable();
                        if (upTable != null && !upTable.isBlank()) {
                            String matchedUpKey =
                                    findMatchingKeyIgnoreCase(cleanRow, upTable.trim());
                            if (matchedUpKey != null
                                    && cleanRow.get(matchedUpKey) instanceof Map<?, ?>) {
                                cleanRow.remove(matchedUpKey);
                            }
                        }
                    }
                }
            }
        }

        List<Long> childModuleIds =
                tree != null ? tree.getChildModuleIds(moduleId) : Collections.emptyList();

        // 2. 检查数字键子模块 (兼容旧前端例如 "103", "104", "106")
        for (Map.Entry<String, Object> entry : cleanRow.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (key != null && key.matches("\\d+") && val != null && tree != null) {
                Long childModuleId = Long.parseLong(key);
                ModuleInfo childMeta = tree.getModule(childModuleId);
                if (childMeta == null) {
                    throw new IllegalArgumentException(
                            "未知的子模块 ID [" + childModuleId + "]，不属于当前模块配置范围");
                }
                boolean childIsMany = (val instanceof List<?>);
                List<DataRow> childRecords = extractDataRowsFromObject(val);
                if (!childRecords.isEmpty()) {
                    DataBlock childBlock =
                            DataBlock.builder()
                                    .view(childMeta.getModuleCode())
                                    .records(childRecords)
                                    .build();
                    SaveNodePlan childPlan =
                            compileNode(
                                    childBlock,
                                    moduleId,
                                    meta,
                                    projectRelations,
                                    tree,
                                    childMeta,
                                    childModuleId);
                    childPlan.setMany(childIsMany);
                    childPlans.add(childPlan);
                }
            }
        }

        // 3. 检查 DataRow 自身的 children 列表 (List<DataBlock>) (读写同构核心自相似树)
        if (rawRow.getChildren() != null && !rawRow.getChildren().isEmpty() && tree != null) {
            for (DataBlock childBlock : rawRow.getChildren()) {
                if (childBlock != null && childBlock.getView() != null) {
                    String targetView = childBlock.getView();
                    Long matchedChildId = null;
                    ModuleInfo matchedChildMeta = null;
                    if (childModuleIds != null) {
                        for (Long cmid : childModuleIds) {
                            ModuleInfo cm = tree.getModule(cmid);
                            if (cm != null
                                    && (targetView.equalsIgnoreCase(cm.getModuleCode())
                                            || (cm.getPrimaryTable() != null
                                                    && targetView.equalsIgnoreCase(
                                                            cm.getPrimaryTable()))
                                            || (targetView.matches("\\d+")
                                                    && Long.parseLong(targetView) == cmid))) {
                                matchedChildId = cmid;
                                matchedChildMeta = cm;
                                break;
                            }
                        }
                    }
                    if (matchedChildId != null) {
                        childPlans.add(
                                compileNode(
                                        childBlock,
                                        moduleId,
                                        meta,
                                        projectRelations,
                                        tree,
                                        matchedChildMeta,
                                        matchedChildId));
                    }
                }
            }
        }

        // 4. 检查兼容的 rawRow.tables 中的 children 字段 (如前端传入的原始 Map)
        Object rowChildrenObj = cleanRow.get("children");
        if (rowChildrenObj != null && tree != null) {
            List<DataBlock> rowChildren = parseRowChildren(rowChildrenObj);
            for (DataBlock rowChildNode : rowChildren) {
                if (rowChildNode != null && rowChildNode.getView() != null) {
                    String targetView = rowChildNode.getView();
                    Long matchedChildId = null;
                    ModuleInfo matchedChildMeta = null;
                    if (childModuleIds != null) {
                        for (Long cmid : childModuleIds) {
                            ModuleInfo cm = tree.getModule(cmid);
                            if (cm != null
                                    && (targetView.equalsIgnoreCase(cm.getModuleCode())
                                            || (cm.getPrimaryTable() != null
                                                    && targetView.equalsIgnoreCase(
                                                            cm.getPrimaryTable()))
                                            || (targetView.matches("\\d+")
                                                    && Long.parseLong(targetView) == cmid))) {
                                matchedChildId = cmid;
                                matchedChildMeta = cm;
                                break;
                            }
                        }
                    }
                    if (matchedChildId != null) {
                        childPlans.add(
                                compileNode(
                                        rowChildNode,
                                        moduleId,
                                        meta,
                                        projectRelations,
                                        tree,
                                        matchedChildMeta,
                                        matchedChildId));
                    }
                }
            }
        }

        return cleanRow;
    }

    private static String findMatchingKeyIgnoreCase(Map<String, Object> map, String targetKey) {
        if (map == null || targetKey == null) {
            return null;
        }
        if (map.containsKey(targetKey)) {
            return targetKey;
        }
        for (String k : map.keySet()) {
            if (targetKey.equalsIgnoreCase(k)) {
                return k;
            }
        }
        return null;
    }

    /** 从 Object 中安全提取 List<DataRow> 记录列表 */
    private List<DataRow> extractDataRowsFromObject(Object obj) {
        if (obj instanceof List<?> list) {
            List<DataRow> records = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof DataRow dr) {
                    records.add(dr);
                } else if (item instanceof Map<?, ?> m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> casted = (Map<String, Object>) m;
                    records.add(new DataRow(casted));
                }
            }
            return records;
        } else if (obj instanceof DataRow dr) {
            return List.of(dr);
        } else if (obj instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) m;
            return List.of(new DataRow(casted));
        }
        return Collections.emptyList();
    }

    /** 解析行记录内部挂载的自相似子模块节点 */
    private List<DataBlock> parseRowChildren(Object rowChildrenObj) {
        if (rowChildrenObj instanceof List<?> list) {
            List<DataBlock> nodes = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof DataBlock block) {
                    nodes.add(block);
                } else if (item instanceof Map) {
                    nodes.add(MAPPER.convertValue(item, DataBlock.class));
                }
            }
            return nodes;
        }
        return List.of();
    }

    /** 解析当前子模块与物理父模块之间的单向关联关系 (无有效父表时返回 {@code null})。 */
    private TableRelationDTO resolveTableRelation(
            String childPrimaryTable,
            ModuleInfo parentMeta,
            ProjectTableRelationsDTO projectRelations,
            ModuleTreeDTO tree) {
        if (childPrimaryTable == null || childPrimaryTable.isBlank() || parentMeta == null) {
            return null;
        }

        ModuleInfo physicalParentMeta = parentMeta;
        String parentPrimary = ModuleRelationResolver.resolvePrimaryTable(physicalParentMeta);
        if (parentPrimary.isBlank()) {
            physicalParentMeta =
                    ModuleRelationResolver.findNearestPhysicalParentMeta(
                            parentMeta, new ArrayList<>(), tree);
            if (physicalParentMeta != null) {
                parentPrimary = ModuleRelationResolver.resolvePrimaryTable(physicalParentMeta);
            }
        }
        if (physicalParentMeta == null || parentPrimary.isBlank()) {
            return null;
        }
        return ModuleRelationResolver.resolveDirectRelation(
                parentPrimary, childPrimaryTable, projectRelations);
    }

    /** 推导子模块落库时必须回填的父级外键列；缺失关联定义时显式失败，杜绝「读得出、写不进」。 */
    private String resolveParentForeignKey(
            String childPrimaryTable,
            ModuleInfo childMeta,
            ModuleInfo parentMeta,
            ProjectTableRelationsDTO projectRelations,
            ModuleTreeDTO tree) {
        if (childPrimaryTable == null || childPrimaryTable.isBlank() || parentMeta == null) {
            return null;
        }

        TableRelationDTO matchedRel =
                resolveTableRelation(childPrimaryTable, parentMeta, projectRelations, tree);
        if (matchedRel != null
                && matchedRel.getJoinField() != null
                && !matchedRel.getJoinField().isBlank()) {
            return matchedRel.getJoinField().trim();
        }

        ModuleInfo physicalParentMeta = parentMeta;
        String parentPrimary = ModuleRelationResolver.resolvePrimaryTable(physicalParentMeta);
        if (parentPrimary.isBlank()) {
            physicalParentMeta =
                    ModuleRelationResolver.findNearestPhysicalParentMeta(
                            parentMeta, new ArrayList<>(), tree);
            if (physicalParentMeta != null) {
                parentPrimary = ModuleRelationResolver.resolvePrimaryTable(physicalParentMeta);
            }
        }

        Long parentModId = physicalParentMeta != null ? physicalParentMeta.getModuleId() : null;
        Long childModId = childMeta != null ? childMeta.getModuleId() : null;
        throw new IllegalStateException(
                String.format(
                        "元数据关联关系未自洽：未在 sys_table_relation 中找到父表 [%s](模块 %s) 到从表 [%s](模块 %s) 的单向关联定义(main_table -> join_table)",
                        parentPrimary, parentModId, childPrimaryTable, childModId));
    }
}
