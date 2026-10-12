package com.jdec.platform.data.biz.util;

import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单据版本派生工具类 (DocumentVersionUtils)
 *
 * <p>服务于单据克隆 (Clone) 与版本派生 (Fork)：深合并增量补丁 (Deep Merge)、剥离树主键并重置审计字段、 解析主表与单据号、向整树下发新版本号。适配自相似
 * DataBlock / DataRow 读写同构体系。
 */
public final class DocumentVersionUtils {

    private static final Set<String> AUDIT_FIELDS =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    "created_at",
                                    "create_time",
                                    "gmt_create",
                                    "updated_at",
                                    "update_time",
                                    "gmt_modified",
                                    "created_by",
                                    "create_by",
                                    "creator",
                                    "updated_by",
                                    "update_by",
                                    "updater")));

    private DocumentVersionUtils() {}

    /**
     * 将局部增量补丁 patch 合并到基准数据 base 上 规则: 1. 主表字段: 复制 base 字段，patch 中存在的字段覆盖更新，其余保留 2. 子表明细行: - patch
     * 中未提及的子表: 100% 完整继承 base 的对应子表 - patch 中提及的子表: * _action == "DELETE": 剔除该行 * 包含有效物理 id: 找到
     * base 中的对应行，打补丁覆盖修改列 * 无 id 或 id <= 0: 作为全新明细行追加 * 未在 patch 中提及且未被删除的原 base 行: 原样保留
     *
     * @param base 基准数据块 (如 156)
     * @param patch 局部增量数据块 (可为 null)
     * @return 深度融合后的新 DataBlock 树
     */
    public static DataBlock deepMerge(DataBlock base, DataBlock patch) {
        if (base == null) {
            return patch != null ? cloneBlock(patch) : null;
        }
        if (patch == null) {
            return cloneBlock(base);
        }

        Long moduleId = base.getModuleId() != null ? base.getModuleId() : patch.getModuleId();
        List<DataRow> baseRecords =
                base.getRecords() != null ? base.getRecords() : Collections.emptyList();
        List<DataRow> patchRecords =
                patch.getRecords() != null ? patch.getRecords() : Collections.emptyList();

        if (baseRecords.isEmpty()) {
            return cloneBlock(patch);
        }

        // 详情场景下，主记录为首行
        DataRow baseFirst = baseRecords.get(0);
        DataRow patchFirst = !patchRecords.isEmpty() ? patchRecords.get(0) : null;

        DataRow mergedRow = mergeDataRow(baseFirst, patchFirst);

        return DataBlock.builder()
                .moduleId(moduleId)
                .records(Collections.singletonList(mergedRow))
                .build();
    }

    /** 合并两个 DataRow (包含 tables 和 children) */
    public static DataRow mergeDataRow(DataRow baseRow, DataRow patchRow) {
        if (baseRow == null) {
            return patchRow != null ? cloneRow(patchRow) : null;
        }
        if (patchRow == null) {
            return cloneRow(baseRow);
        }

        DataRow mergedRow = new DataRow();

        // 1. 合并 tables (物理表及其字段)
        Map<String, Object> baseTables =
                baseRow.getTables() != null ? baseRow.getTables() : Collections.emptyMap();
        Map<String, Object> patchTables =
                patchRow.getTables() != null ? patchRow.getTables() : Collections.emptyMap();

        Set<String> allTableKeys = new HashSet<>();
        allTableKeys.addAll(baseTables.keySet());
        allTableKeys.addAll(patchTables.keySet());

        for (String tableKey : allTableKeys) {
            Object baseVal = baseTables.get(tableKey);
            Object patchVal = patchTables.get(tableKey);

            if (patchVal == null) {
                mergedRow.setTable(tableKey, cloneObject(baseVal));
            } else if (baseVal instanceof Map<?, ?> baseMap
                    && patchVal instanceof Map<?, ?> patchMap) {
                // 表对象深度覆盖合并: base 的列为基底，patch 的列覆盖
                Map<String, Object> mergedTable = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : baseMap.entrySet()) {
                    if (e.getKey() != null) {
                        mergedTable.put(String.valueOf(e.getKey()), cloneObject(e.getValue()));
                    }
                }
                for (Map.Entry<?, ?> e : patchMap.entrySet()) {
                    if (e.getKey() != null) {
                        mergedTable.put(String.valueOf(e.getKey()), cloneObject(e.getValue()));
                    }
                }
                mergedRow.setTable(tableKey, mergedTable);
            } else {
                // 直接值字段覆盖
                mergedRow.setTable(tableKey, cloneObject(patchVal));
            }
        }

        // 2. 合并 children (自相似挂载的 List<DataBlock>)
        List<DataBlock> baseChildren =
                baseRow.getChildren() != null ? baseRow.getChildren() : Collections.emptyList();
        List<DataBlock> patchChildren =
                patchRow.getChildren() != null ? patchRow.getChildren() : Collections.emptyList();

        Map<Long, DataBlock> baseChildMap = new LinkedHashMap<>();
        for (DataBlock bc : baseChildren) {
            if (bc != null && bc.getModuleId() != null) {
                baseChildMap.put(bc.getModuleId(), bc);
            }
        }

        Map<Long, DataBlock> patchChildMap = new LinkedHashMap<>();
        for (DataBlock pc : patchChildren) {
            if (pc != null && pc.getModuleId() != null) {
                patchChildMap.put(pc.getModuleId(), pc);
            }
        }

        Set<Long> allChildModules = new HashSet<>();
        allChildModules.addAll(baseChildMap.keySet());
        allChildModules.addAll(patchChildMap.keySet());

        List<DataBlock> mergedChildren = new ArrayList<>();

        for (Long childModId : allChildModules) {
            DataBlock baseChildBlock = baseChildMap.get(childModId);
            DataBlock patchChildBlock = patchChildMap.get(childModId);

            if (patchChildBlock == null) {
                // patch 完全没动该子模块 -> 100% 继承
                mergedChildren.add(cloneBlock(baseChildBlock));
            } else if (baseChildBlock == null) {
                // 原 base 没有该子模块 -> 纯新增
                List<DataRow> newRows = new ArrayList<>();
                if (patchChildBlock.getRecords() != null) {
                    for (DataRow pr : patchChildBlock.getRecords()) {
                        newRows.add(cloneRow(pr));
                    }
                }
                mergedChildren.add(
                        DataBlock.builder().moduleId(childModId).records(newRows).build());
            } else {
                // 两边均有数据 -> 做行级比对与合并
                List<DataRow> mergedChildRows =
                        mergeChildRows(baseChildBlock.getRecords(), patchChildBlock.getRecords());
                mergedChildren.add(
                        DataBlock.builder().moduleId(childModId).records(mergedChildRows).build());
            }
        }

        mergedRow.setChildren(mergedChildren);
        return mergedRow;
    }

    /**
     * 读写同构下的明细行合并策略: 前端提交的 patchRows 代表该子模块当前被编辑后的全量状态 1. patchRow 带有旧 id: 在 baseRows
     * 中定位该行，以原行字段为底本，用 patchRow 覆盖修改的字段 (未改字段完整继承) 2. patchRow 无 id: 作为纯新增行追加 3. 在原 baseRows
     * 中存在、但未出现在 patchRows 中的行: 用户在界面上已删除该行，自然剔除，不进入新版本
     */
    private static List<DataRow> mergeChildRows(List<DataRow> baseRows, List<DataRow> patchRows) {
        if (baseRows == null) {
            baseRows = Collections.emptyList();
        }
        if (patchRows == null) {
            patchRows = Collections.emptyList();
        }

        // 构建 base 索引字典 (通过物理主键 id)
        Map<String, DataRow> baseMapById = new HashMap<>();
        for (DataRow brow : baseRows) {
            Object id = extractId(brow);
            if (id != null) {
                baseMapById.put(id.toString(), brow);
            }
        }

        List<DataRow> result = new ArrayList<>();

        for (DataRow prow : patchRows) {
            Object pid = extractId(prow);

            if (pid != null && baseMapById.containsKey(pid.toString())) {
                // 带有效旧 id: 以原行数据为底，深度融合覆盖本次修改的字段
                DataRow baseItem = baseMapById.get(pid.toString());
                result.add(mergeDataRow(baseItem, prow));
            } else {
                // 无 id 或新增行: 直接作为全新行加入
                result.add(cloneRow(prow));
            }
        }

        return result;
    }

    public static Object extractId(DataRow row) {
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

    /** 从 DataBlock 根节点提取主物理表名 (存放 business_no 的主表) */
    public static String extractPrimaryTable(DataBlock block) {
        if (block == null || block.getRecords() == null || block.getRecords().isEmpty()) {
            return null;
        }
        DataRow rootRow = block.getRecords().get(0);
        if (rootRow == null || rootRow.getTables() == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : rootRow.getTables().entrySet()) {
            String tableKey = entry.getKey();
            Object val = entry.getValue();
            if (tableKey != null && !tableKey.matches("\\d+") && !"children".equals(tableKey)) {
                if (val instanceof Map<?, ?> map) {
                    if (map.containsKey("business_no") || map.containsKey("BUSINESS_NO")) {
                        return tableKey;
                    }
                }
            }
        }
        for (Map.Entry<String, Object> entry : rootRow.getTables().entrySet()) {
            String tableKey = entry.getKey();
            if (tableKey != null && !tableKey.matches("\\d+") && !"children".equals(tableKey)) {
                if (entry.getValue() instanceof Map<?, ?>) {
                    return tableKey;
                }
            }
        }
        return null;
    }

    /** 从 DataBlock 根节点提取业务单据号 business_no */
    public static String extractBusinessNo(DataBlock block) {
        if (block == null || block.getRecords() == null || block.getRecords().isEmpty()) {
            return null;
        }
        DataRow rootRow = block.getRecords().get(0);
        if (rootRow == null || rootRow.getTables() == null) {
            return null;
        }
        Object bNo = rootRow.getTables().get("business_no");
        if (bNo != null && !bNo.toString().isBlank()) {
            return bNo.toString();
        }
        for (Object val : rootRow.getTables().values()) {
            if (val instanceof Map<?, ?> map) {
                Object subBNo = map.get("business_no");
                if (subBNo != null && !subBNo.toString().isBlank()) {
                    return subBNo.toString();
                }
            }
        }
        return null;
    }

    /**
     * 递归将整棵数据树 (DataBlock -> DataRow -> tables 及嵌套子表/children) 所有物理表记录的 version_no 统一设置为指定的
     * versionNo
     */
    public static void applyVersionNo(DataBlock block, Integer versionNo) {
        if (block == null || block.getRecords() == null || versionNo == null) {
            return;
        }
        for (DataRow row : block.getRecords()) {
            applyRowVersionNo(row, versionNo);
        }
    }

    public static void applyRowVersionNo(DataRow row, Integer versionNo) {
        if (row == null || versionNo == null) {
            return;
        }

        if (row.getTables() != null) {
            row.getTables().put("version_no", versionNo);
            for (Object val : row.getTables().values()) {
                if (val instanceof Map<?, ?> subMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typedSubMap = (Map<String, Object>) subMap;
                    typedSubMap.put("version_no", versionNo);
                }
            }
        }

        if (row.getChildren() != null) {
            for (DataBlock childBlock : row.getChildren()) {
                applyVersionNo(childBlock, versionNo);
            }
        }
    }

    /** 递归清洗数据树上的所有主键 ID 与关联外键，并重置审计字段 使得该树完全变成一个适合全新 INSERT 的纯净数据树 */
    public static void stripIdsAndResetAudit(DataBlock block) {
        if (block == null || block.getRecords() == null) {
            return;
        }
        for (DataRow row : block.getRecords()) {
            stripRowIdsAndResetAudit(row);
        }
    }

    public static void stripRowIdsAndResetAudit(DataRow row) {
        if (row == null) {
            return;
        }

        if (row.getTables() != null) {
            cleanMapAudit(row.getTables());
            for (Object val : row.getTables().values()) {
                if (val instanceof Map<?, ?> subMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typedSubMap = (Map<String, Object>) subMap;
                    cleanMapAudit(typedSubMap);
                }
            }
        }

        if (row.getChildren() != null) {
            for (DataBlock childBlock : row.getChildren()) {
                stripIdsAndResetAudit(childBlock);
            }
        }
    }

    private static void cleanMapAudit(Map<String, Object> map) {
        if (map == null) {
            return;
        }
        map.remove("id");
        map.remove("ID");

        for (String auditField : AUDIT_FIELDS) {
            map.remove(auditField);
            map.remove(auditField.toUpperCase());
        }

        if (map.containsKey("deleted") || map.containsKey("DELETED")) {
            map.put("deleted", 0);
        }
    }

    public static DataBlock cloneBlock(DataBlock source) {
        if (source == null) {
            return null;
        }
        List<DataRow> copyRecords = new ArrayList<>();
        if (source.getRecords() != null) {
            for (DataRow r : source.getRecords()) {
                copyRecords.add(cloneRow(r));
            }
        }
        return DataBlock.builder().moduleId(source.getModuleId()).records(copyRecords).build();
    }

    public static DataRow cloneRow(DataRow source) {
        if (source == null) {
            return null;
        }
        DataRow copy = new DataRow();
        if (source.getTables() != null) {
            for (Map.Entry<String, Object> entry : source.getTables().entrySet()) {
                copy.setTable(entry.getKey(), cloneObject(entry.getValue()));
            }
        }
        if (source.getChildren() != null) {
            List<DataBlock> copyChildren = new ArrayList<>();
            for (DataBlock cb : source.getChildren()) {
                copyChildren.add(cloneBlock(cb));
            }
            copy.setChildren(copyChildren);
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Object cloneObject(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    copy.put(String.valueOf(e.getKey()), cloneObject(e.getValue()));
                }
            }
            return copy;
        } else if (obj instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(cloneObject(item));
            }
            return copy;
        }
        return obj;
    }
}
