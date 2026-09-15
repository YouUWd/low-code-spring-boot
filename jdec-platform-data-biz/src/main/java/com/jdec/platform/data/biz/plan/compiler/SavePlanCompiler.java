package com.jdec.platform.data.biz.plan.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.data.api.dto.common.TableRelationDTO;
import com.jdec.platform.data.api.dto.model.ModuleInfo;
import com.jdec.platform.data.api.dto.request.SaveReq;
import com.jdec.platform.data.biz.plan.model.SaveNodePlan;
import com.jdec.platform.data.biz.plan.model.SavePlan;
import com.jdec.platform.data.biz.service.MetaCacheService;
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
 * <p>职责：递归解析请求保存树，校验各模块元数据，推导父子外键传播规则，编译生成完整的 SavePlan。 支持模块级 children 与行记录内部嵌套的 children (与查询响应
 * 100% 读写同构)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SavePlanCompiler {

    private final MetaCacheService metaCacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 编译顶层保存请求为 SavePlan (支持标准 SaveReq 或直接将 detail.data 作为单条记录回传) */
    public SavePlan compile(SaveReq req) {
        if (req == null) {
            throw new IllegalArgumentException("保存请求不能为空");
        }

        // 自适应支持方案 A：如果未显式指定 moduleId，且 records 为空但自身为单对象（或前端直接回传了 data 载荷）
        // 尝试从单条 record 或自身直接推导根模块 ID
        if (req.getModuleId() == null) {
            req = normalizeTopLevelSaveReq(req);
        }

        if (req.getModuleId() == null) {
            throw new IllegalArgumentException("保存请求或模块 ID 不能为空");
        }

        SaveNodePlan rootPlan = compileNode(req, null, null);
        return SavePlan.builder().rootNodePlan(rootPlan).build();
    }

    /** 自适应推导并规范化顶层 SaveReq (如将 {"1001": {...}} 自动识别为 moduleId=1001) */
    private SaveReq normalizeTopLevelSaveReq(SaveReq req) {
        if (req.getRecords() != null && !req.getRecords().isEmpty()) {
            Map<String, Object> firstRow = req.getRecords().get(0);
            if (firstRow != null) {
                for (Map.Entry<String, Object> entry : firstRow.entrySet()) {
                    if (entry.getKey() != null && entry.getKey().matches("\\d+")) {
                        Long inferredModId = Long.parseLong(entry.getKey());
                        return req.toBuilder().moduleId(inferredModId).build();
                    }
                }
            }
        }
        return req;
    }

    /** 递归编译单节点保存计划 */
    public SaveNodePlan compileNode(SaveReq node, Long parentModuleId, ModuleInfo parentMeta) {

        Long moduleId = node.getModuleId();
        ModuleInfo meta = metaCacheService.getModuleComplete(moduleId);
        if (meta == null) {
            throw new IllegalArgumentException("模块 ID [" + moduleId + "] 不存在或未配置元数据");
        }

        String primaryTable = resolvePrimaryTable(meta);
        String parentFk = resolveParentForeignKey(primaryTable, meta, parentMeta);

        List<SaveNodePlan> childPlans = new ArrayList<>();

        // 1. 处理模块级 children
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            for (SaveReq childNode : node.getChildren()) {
                if (childNode != null && childNode.getModuleId() != null) {
                    childPlans.add(compileNode(childNode, moduleId, meta));
                }
            }
        }

        // 2. 规范化当前节点的 records，并提取行内嵌套的 children (读写同构)
        List<Map<String, Object>> sourceRecords = node.getRecords();
        List<Map<String, Object>> cleanRecords = new ArrayList<>();

        if (sourceRecords != null && !sourceRecords.isEmpty()) {
            for (Map<String, Object> rawRow : sourceRecords) {
                if (rawRow == null) {
                    continue;
                }
                Map<String, Object> cleanRow =
                        normalizeRow(rawRow, primaryTable, moduleId, meta, childPlans);
                cleanRecords.add(cleanRow);
            }
        }

        return SaveNodePlan.builder()
                .moduleId(moduleId)
                .moduleMeta(meta)
                .primaryTable(primaryTable)
                .parentForeignKey(parentFk)
                .records(cleanRecords)
                .children(childPlans)
                .build();
    }

    /** 规范化行记录，支持解包物理表对象与提取行内数字键挂载的子模块 */
    private Map<String, Object> normalizeRow(
            Map<String, Object> rawRow,
            String primaryTable,
            Long moduleId,
            ModuleInfo meta,
            List<SaveNodePlan> childPlans) {
        Map<String, Object> cleanRow = new HashMap<>(rawRow);
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
                List<Map<String, Object>> childRecords = extractRecordsFromObject(val);
                if (!childRecords.isEmpty()) {
                    SaveReq childReq =
                            SaveReq.builder().moduleId(childModuleId).records(childRecords).build();
                    try {
                        childPlans.add(compileNode(childReq, moduleId, meta));
                    } catch (Exception e) {
                        log.warn("编译行内子模块 [{}] 失败: {}", childModuleId, e.getMessage());
                    }
                }
            }
        }

        // 3. 检查兼容的 children 字段
        Object rowChildrenObj = rawRow.get("children");
        if (rowChildrenObj != null) {
            List<SaveReq> rowChildren = parseRowChildren(rowChildrenObj);
            for (SaveReq rowChildNode : rowChildren) {
                if (rowChildNode != null && rowChildNode.getModuleId() != null) {
                    childPlans.add(compileNode(rowChildNode, moduleId, meta));
                }
            }
        }

        return cleanRow;
    }

    /** 从 Object 中安全提取 List<Map<String, Object>> 记录列表 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRecordsFromObject(Object obj) {
        if (obj instanceof List<?> list) {
            List<Map<String, Object>> records = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    records.add((Map<String, Object>) m);
                }
            }
            return records;
        } else if (obj instanceof Map<?, ?> m) {
            return List.of((Map<String, Object>) m);
        }
        return Collections.emptyList();
    }

    /** 解析行记录内部挂载的自相似子模块节点 */
    private List<SaveReq> parseRowChildren(Object rowChildrenObj) {
        if (rowChildrenObj instanceof List<?> list) {
            List<SaveReq> nodes = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof SaveReq saveNode) {
                    nodes.add(saveNode);
                } else if (item instanceof Map) {
                    try {
                        SaveReq converted = objectMapper.convertValue(item, SaveReq.class);
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
            String childPrimaryTable, ModuleInfo childMeta, ModuleInfo parentMeta) {
        if (childPrimaryTable == null || childPrimaryTable.isBlank() || parentMeta == null) {
            return null;
        }

        List<ModuleInfo> intermediateVms = new ArrayList<>();
        ModuleInfo physicalParentMeta = parentMeta;
        String parentPrimary = resolvePrimaryTable(physicalParentMeta);

        if (parentPrimary == null || parentPrimary.isBlank()) {
            intermediateVms.add(parentMeta);
            physicalParentMeta = findNearestPhysicalParentMeta(parentMeta, intermediateVms);
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

        List<TableRelationDTO> allRelations = new ArrayList<>();
        if (childMeta != null && childMeta.getTableRelations() != null) {
            allRelations.addAll(childMeta.getTableRelations());
        }
        for (ModuleInfo vm : intermediateVms) {
            if (vm != null && vm.getTableRelations() != null) {
                allRelations.addAll(vm.getTableRelations());
            }
        }
        if (physicalParentMeta.getTableRelations() != null) {
            allRelations.addAll(physicalParentMeta.getTableRelations());
        }

        for (TableRelationDTO rel : allRelations) {
            if (rel != null
                    && parentPrimary.equalsIgnoreCase(rel.getMainTable())
                    && childPrimaryTable.equalsIgnoreCase(rel.getJoinTable())) {
                if (rel.getJoinField() != null && !rel.getJoinField().isBlank()) {
                    return rel.getJoinField().trim();
                }
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
            ModuleInfo currentMeta, List<ModuleInfo> intermediateCollector) {
        if (currentMeta == null) {
            return null;
        }
        Long curParentId = currentMeta.getParentId();
        Set<Long> visited = new HashSet<>();
        while (curParentId != null && curParentId > 0 && visited.add(curParentId)) {
            ModuleInfo pMeta = metaCacheService.getModuleComplete(curParentId);
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
}
