package com.jdec.platform.data.biz.util;

import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
                                    "created_date",
                                    "create_date",
                                    "gmt_create",
                                    "updated_at",
                                    "update_time",
                                    "updated_date",
                                    "update_date",
                                    "gmt_modified",
                                    "created_by",
                                    "create_by",
                                    "created_name",
                                    "creator",
                                    "updated_by",
                                    "update_by",
                                    "updated_name",
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

        String view =
                base != null && base.getView() != null
                        ? base.getView()
                        : (patch != null ? patch.getView() : null);

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

        return DataBlock.builder().view(view).records(Collections.singletonList(mergedRow)).build();
    }

    /** 合并两个 DataRow (包含 tables 和 children) */
    public static DataRow mergeDataRow(DataRow baseRow, DataRow patchRow) {
        if (baseRow == null) {
            return patchRow != null ? cloneRow(patchRow) : null;
        }
        if (patchRow == null) {
            return cloneRow(baseRow);
        }

        // 先克隆 baseRow，再以 patchRow 的表字段逐项覆盖，彻底杜绝扁平键与嵌套表 Map 混存时因遍历顺序导致的旧值回盖
        DataRow mergedRow = cloneRow(baseRow);

        Map<String, Object> patchTables =
                patchRow.getTables() != null ? patchRow.getTables() : Collections.emptyMap();

        for (Map.Entry<String, Object> entry : patchTables.entrySet()) {
            String key = entry.getKey();
            Object patchVal = entry.getValue();
            if (key == null || patchVal == null) {
                continue;
            }
            if (patchVal instanceof Map<?, ?> patchMap) {
                Map<String, Object> existingTable = mergedRow.getTable(key);
                Map<String, Object> mergedTable = new LinkedHashMap<>();
                if (existingTable != null) {
                    existingTable.forEach(
                            (k, v) -> {
                                if (k != null) {
                                    mergedTable.put(String.valueOf(k), cloneObject(v));
                                }
                            });
                }
                for (Map.Entry<?, ?> e : patchMap.entrySet()) {
                    if (e.getKey() != null) {
                        mergedTable.put(String.valueOf(e.getKey()), cloneObject(e.getValue()));
                    }
                }
                mergedRow.putTable(key, mergedTable);
            } else {
                mergedRow.put(key, cloneObject(patchVal));
            }
        }

        // 2. 合并 children (自相似挂载的 List<DataBlock>)
        List<DataBlock> baseChildren =
                baseRow.getChildren() != null ? baseRow.getChildren() : Collections.emptyList();
        List<DataBlock> patchChildren =
                patchRow.getChildren() != null ? patchRow.getChildren() : Collections.emptyList();

        Map<String, DataBlock> baseChildMap = new LinkedHashMap<>();
        for (DataBlock bc : baseChildren) {
            if (bc != null && bc.getView() != null) {
                baseChildMap.put(bc.getView().toUpperCase(), bc);
            }
        }

        Map<String, DataBlock> patchChildMap = new LinkedHashMap<>();
        for (DataBlock pc : patchChildren) {
            if (pc != null && pc.getView() != null) {
                patchChildMap.put(pc.getView().toUpperCase(), pc);
            }
        }

        Set<String> allChildViews = new LinkedHashSet<>();
        allChildViews.addAll(baseChildMap.keySet());
        allChildViews.addAll(patchChildMap.keySet());

        List<DataBlock> mergedChildren = new ArrayList<>();

        for (String childView : allChildViews) {
            DataBlock baseChildBlock = baseChildMap.get(childView);
            DataBlock patchChildBlock = patchChildMap.get(childView);

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
                        DataBlock.builder()
                                .view(patchChildBlock.getView())
                                .records(newRows)
                                .build());
            } else {
                // 两边均有数据 -> 做行级比对与合并
                List<DataRow> mergedChildRows =
                        mergeChildRows(baseChildBlock.getRecords(), patchChildBlock.getRecords());
                String view =
                        baseChildBlock.getView() != null
                                ? baseChildBlock.getView()
                                : patchChildBlock.getView();
                mergedChildren.add(DataBlock.builder().view(view).records(mergedChildRows).build());
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
        for (Map.Entry<String, Object> entry : row.getTables().entrySet()) {
            String key = entry.getKey();
            if (key != null && (key.equalsIgnoreCase("id") || key.toLowerCase().endsWith(".id"))) {
                if (entry.getValue() != null) {
                    return entry.getValue();
                }
            }
            if (entry.getValue() instanceof Map<?, ?> map) {
                Object subId = map.get("id");
                if (subId == null) {
                    subId = map.get("ID");
                }
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
        for (String key : rootRow.getTables().keySet()) {
            if (key != null && key.toLowerCase().endsWith(".business_no")) {
                int dot = key.indexOf('.');
                if (dot > 0) {
                    return key.substring(0, dot).trim();
                }
            }
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
        if (block.getView() != null && !block.getView().isBlank()) {
            return block.getView();
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
        for (Map.Entry<String, Object> entry : rootRow.getTables().entrySet()) {
            String key = entry.getKey();
            if (key != null
                    && (key.equalsIgnoreCase("business_no")
                            || key.toLowerCase().endsWith(".business_no"))) {
                if (entry.getValue() != null && !entry.getValue().toString().isBlank()) {
                    return entry.getValue().toString();
                }
            }
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
            boolean hasTableMap = false;
            for (Object val : row.getTables().values()) {
                if (val instanceof Map<?, ?> subMap) {
                    hasTableMap = true;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typedSubMap = (Map<String, Object>) subMap;
                    typedSubMap.put("version_no", versionNo);
                }
            }
            for (String k : new ArrayList<>(row.getTables().keySet())) {
                if (k != null && k.toLowerCase().endsWith(".version_no")) {
                    row.getTables().put(k, versionNo);
                }
            }
            if (!hasTableMap) {
                row.getTables().put("version_no", versionNo);
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

    private static boolean isAuditOrIdKey(String key) {
        if (key == null) {
            return false;
        }
        String col = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;
        String lowerCol = col.toLowerCase();
        if ("id".equals(lowerCol)) {
            return true;
        }
        return AUDIT_FIELDS.contains(lowerCol);
    }

    private static void cleanMapAudit(Map<String, Object> map) {
        if (map == null) {
            return;
        }
        List<String> toRemove = new ArrayList<>();
        for (String k : map.keySet()) {
            if (isAuditOrIdKey(k)) {
                toRemove.add(k);
            }
        }
        toRemove.forEach(map::remove);

        for (String k : new ArrayList<>(map.keySet())) {
            String col = k.contains(".") ? k.substring(k.lastIndexOf('.') + 1) : k;
            if ("deleted".equalsIgnoreCase(col)) {
                map.put(k, 0);
            }
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
        return DataBlock.builder().view(source.getView()).records(copyRecords).build();
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
