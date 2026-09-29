package com.jdec.platform.data.biz.plan.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.request.SaveReq;
import com.jdec.platform.data.biz.plan.model.SaveNodePlan;
import com.jdec.platform.data.biz.plan.model.SavePlan;
import com.jdec.platform.shared.context.AppContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 结构化保存计划编译器 (SavePlanCompiler)
 *
 * <p>职责：递归解析业务数据区块 (DataBlock) 保存树，校验各模块元数据，推导父子外键传播规则，编译生成完整的 SavePlan。 支持行记录内部嵌套的 children (与查询响应
 * 100% 读写同构)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SavePlanCompiler {

    private final ConfigApi configApi;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 核心编译入口：编译 DataBlock 业务数据区块为 SavePlan */
    public SavePlan compile(DataBlock block) {
        if (block == null) {
            throw new IllegalArgumentException("保存请求或数据区块不能为空");
        }

        Long moduleId = block.getModuleId();
        if (moduleId == null && block.getRecords() != null && !block.getRecords().isEmpty()) {
            DataRow firstRow = block.getRecords().get(0);
            if (firstRow != null && firstRow.getTables() != null) {
                for (Map.Entry<String, Object> entry : firstRow.getTables().entrySet()) {
                    if (entry.getKey() != null && entry.getKey().matches("\\d+")) {
                        moduleId = Long.parseLong(entry.getKey());
                        break;
                    }
                }
            }
        }

        if (moduleId == null) {
            throw new IllegalArgumentException("保存请求或模块 ID 不能为空");
        }

        block = block.toBuilder().moduleId(moduleId).build();

        ModuleTreeDTO tree = loadModuleTree(moduleId);
        ProjectTableRelationsDTO projectRelations = getProjectRelations();
        SaveNodePlan rootPlan = compileNode(block, null, null, projectRelations, tree);
        return SavePlan.builder().rootNodePlan(rootPlan).build();
    }

    /** 兼容编译入口：编译 SaveReq 为 SavePlan */
    public SavePlan compile(SaveReq req) {
        if (req == null) {
            throw new IllegalArgumentException("保存请求不能为空");
        }
        DataBlock block =
                DataBlock.builder().moduleId(req.getModuleId()).records(req.getRecords()).build();
        return compile(block);
    }

    /** 递归编译单节点保存计划 (内部共享 projectRelations 与 ModuleTreeDTO) */
    public SaveNodePlan compileNode(
            DataBlock node,
            Long parentModuleId,
            ModuleInfo parentMeta,
            ProjectTableRelationsDTO projectRelations,
            ModuleTreeDTO tree) {

        Long moduleId = node.getModuleId();
        ModuleInfo meta = tree != null ? tree.getModule(moduleId) : null;
        if (meta == null) {
            throw new IllegalArgumentException("模块 ID [" + moduleId + "] 不存在或未在当前模块树中配置元数据");
        }

        String primaryTable = resolvePrimaryTable(meta);
        String parentFk =
                resolveParentForeignKey(primaryTable, meta, parentMeta, projectRelations, tree);

        List<SaveNodePlan> childPlans = new ArrayList<>();

        // 规范化当前节点的 records，并提取行内嵌套的 children (DataBlock 读写同构)
        List<DataRow> sourceRecords = node.getRecords();
        List<Map<String, Object>> cleanRecords = new ArrayList<>();
        List<DataRow> cleanSourceRows = new ArrayList<>();

        if (sourceRecords != null && !sourceRecords.isEmpty()) {
            for (DataRow rawRow : sourceRecords) {
                if (rawRow == null) {
                    continue;
                }
                Map<String, Object> cleanRow =
                        normalizeRow(
                                rawRow,
                                primaryTable,
                                moduleId,
                                meta,
                                childPlans,
                                projectRelations,
                                tree);
                cleanRecords.add(cleanRow);
                cleanSourceRows.add(rawRow);
            }
        }

        boolean isMany = node.getRecords() != null && node.getRecords().size() > 1;

        return SaveNodePlan.builder()
                .moduleId(moduleId)
                .moduleMeta(meta)
                .primaryTable(primaryTable)
                .parentForeignKey(parentFk)
                .isMany(isMany)
                .records(cleanRecords)
                .sourceDataRows(cleanSourceRows)
                .children(childPlans)
                .build();
    }

    /** 规范化行记录，支持解包物理表对象与提取行内挂载的子模块 (DataRow) */
    private Map<String, Object> normalizeRow(
            DataRow rawRow,
            String primaryTable,
            Long moduleId,
            ModuleInfo meta,
            List<SaveNodePlan> childPlans,
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

        // 1. 如果包含主表名子对象，展平合并到当前行
        if (primaryTable != null && cleanRow.get(primaryTable) instanceof Map<?, ?> tableMap) {
            for (Map.Entry<?, ?> entry : tableMap.entrySet()) {
                if (entry.getKey() != null) {
                    cleanRow.putIfAbsent(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }

        // 2. 检查数字键子模块 (例如 "103", "104", "106")
        for (Map.Entry<String, Object> entry : cleanRow.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (key != null && key.matches("\\d+") && val != null) {
                Long childModuleId = Long.parseLong(key);
                boolean childIsMany = (val instanceof List<?>);
                List<DataRow> childRecords = extractDataRowsFromObject(val);
                if (!childRecords.isEmpty()) {
                    DataBlock childBlock =
                            DataBlock.builder()
                                    .moduleId(childModuleId)
                                    .records(childRecords)
                                    .build();
                    try {
                        SaveNodePlan childPlan =
                                compileNode(childBlock, moduleId, meta, projectRelations, tree);
                        childPlan.setMany(childIsMany);
                        childPlans.add(childPlan);
                    } catch (Exception e) {
                        log.warn("编译行内子模块 [{}] 失败: {}", childModuleId, e.getMessage());
                    }
                }
            }
        }

        // 3. 检查 DataRow 自身的 children 列表 (List<DataBlock>)
        if (rawRow.getChildren() != null && !rawRow.getChildren().isEmpty()) {
            for (DataBlock childBlock : rawRow.getChildren()) {
                if (childBlock != null && childBlock.getModuleId() != null) {
                    try {
                        SaveNodePlan childPlan =
                                compileNode(childBlock, moduleId, meta, projectRelations, tree);
                        childPlans.add(childPlan);
                    } catch (Exception e) {
                        log.warn(
                                "编译 DataRow children 子模块 [{}] 失败: {}",
                                childBlock.getModuleId(),
                                e.getMessage());
                    }
                }
            }
        }

        // 4. 检查兼容的 rawRow.tables 中的 children 字段 (如前端传入的原始 Map)
        Object rowChildrenObj = cleanRow.get("children");
        if (rowChildrenObj != null) {
            List<DataBlock> rowChildren = parseRowChildren(rowChildrenObj);
            for (DataBlock rowChildNode : rowChildren) {
                if (rowChildNode != null && rowChildNode.getModuleId() != null) {
                    childPlans.add(
                            compileNode(rowChildNode, moduleId, meta, projectRelations, tree));
                }
            }
        }

        return cleanRow;
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
                    try {
                        DataBlock converted = objectMapper.convertValue(item, DataBlock.class);
                        nodes.add(converted);
                    } catch (Exception e) {
                        log.warn("解析行内嵌套子模块失败: {}", item, e);
                    }
                }
            }
            return nodes;
        }
        return List.of();
    }

    private String resolvePrimaryTable(ModuleInfo meta) {
        if (meta == null) {
            return "";
        }
        if (meta.getPrimaryTable() != null && !meta.getPrimaryTable().isBlank()) {
            return meta.getPrimaryTable().trim();
        }
        return "";
    }

    private String resolveParentForeignKey(
            String childPrimaryTable,
            ModuleInfo childMeta,
            ModuleInfo parentMeta,
            ProjectTableRelationsDTO projectRelations,
            ModuleTreeDTO tree) {
        if (childPrimaryTable == null || childPrimaryTable.isBlank() || parentMeta == null) {
            return null;
        }

        List<ModuleInfo> intermediateVms = new ArrayList<>();
        ModuleInfo physicalParentMeta = parentMeta;
        String parentPrimary = resolvePrimaryTable(physicalParentMeta);

        if (parentPrimary == null || parentPrimary.isBlank()) {
            intermediateVms.add(parentMeta);
            physicalParentMeta = findNearestPhysicalParentMeta(parentMeta, intermediateVms, tree);
            if (physicalParentMeta != null) {
                parentPrimary = resolvePrimaryTable(physicalParentMeta);
            }
        }

        if (physicalParentMeta == null || parentPrimary == null || parentPrimary.isBlank()) {
            return null;
        }

        // 第一性原理特殊情况：当子模块物理主表与父模块物理主表为同一物理表时 (同一实体的业务视图拆分)，外键即自身主键 id
        if (parentPrimary.equalsIgnoreCase(childPrimaryTable)) {
            return "id";
        }

        TableRelationDTO matchedRel = null;
        if (projectRelations != null) {
            matchedRel = projectRelations.findDirectRelation(parentPrimary, childPrimaryTable);
        }

        if (matchedRel != null) {
            if (matchedRel.getJoinField() != null && !matchedRel.getJoinField().isBlank()) {
                return matchedRel.getJoinField().trim();
            }
        }

        Long parentModId = physicalParentMeta.getModuleId();
        Long childModId = childMeta != null ? childMeta.getModuleId() : null;
        throw new IllegalStateException(
                String.format(
                        "元数据关联关系未自洽：未在 sys_table_relation 中找到父表 [%s](模块 %s) 到从表 [%s](模块 %s) 的单向关联定义(main_table -> join_table)",
                        parentPrimary, parentModId, childPrimaryTable, childModId));
    }

    private ModuleInfo findNearestPhysicalParentMeta(
            ModuleInfo currentMeta, List<ModuleInfo> intermediateCollector, ModuleTreeDTO tree) {
        if (currentMeta == null) {
            return null;
        }
        Long curParentId = currentMeta.getParentId();
        Set<Long> visited = new HashSet<>();
        while (curParentId != null && curParentId > 0 && visited.add(curParentId)) {
            ModuleInfo pMeta = tree != null ? tree.getModule(curParentId) : null;
            if (pMeta == null) {
                break;
            }
            String pTable = resolvePrimaryTable(pMeta);
            if (pTable != null && !pTable.isBlank()) {
                return pMeta;
            }
            if (intermediateCollector != null) {
                intermediateCollector.add(pMeta);
            }
            curParentId = pMeta.getParentId();
        }
        return null;
    }

    private ModuleTreeDTO loadModuleTree(Long rootModuleId) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        return configApi.getModuleTree(projectNo, subjectId, rootModuleId);
    }

    private ProjectTableRelationsDTO getProjectRelations() {
        String projectNo = AppContext.getProjectNo();
        return configApi.getProjectTableRelations(projectNo);
    }
}
