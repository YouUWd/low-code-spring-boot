package com.jdec.platform.dataengine.biz.third.api.data.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jdec.platform.config.api.dto.common.*;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.biz.dto.VersionSnapShot;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.JSON;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/** 模块树字段对比数据生成类 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModuleTreeDiffService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private static final Set<String> EXCLUDE_FIELDS =
            Set.of(
                    "id",
                    "pid",
                    "version_no",
                    "created_by",
                    "updated_by",
                    "created_date",
                    "updated_date",
                    "source_id");

    private static final Set<String> REMOVED_FIELDS = Set.of("id", "pid", "relation_no");

    /**
     * 入口：对比两棵模块树，【新树】原地增加 changeList
     *
     * @param oldTree 旧版本json树（只读）
     * @param newTree 新版本json树（原地增加changeList）
     */
    public void diffModuleTree(
            NestedModuleDataDTO oldTree,
            NestedModuleDataDTO newTree,
            Map<Long, Map<Long, Map<String, VersionSnapShot>>> currentVersionMap,
            ProjectTableRelationsDTO projectTableRelationsDTO,
            ModuleTreeDTO moduleTreeDTO) {
        // 构建旧树 moduleId -> recordMap(业务id -> recordNode)
        Map<Long, Map<String, RecordDataDTO>> oldModuleRecordMap =
                buildOldModuleRecordIndex(oldTree, moduleTreeDTO);
        recursiveDiff(
                oldTree,
                newTree,
                oldModuleRecordMap,
                currentVersionMap,
                projectTableRelationsDTO,
                moduleTreeDTO);
    }

    /** 递归处理模块节点 */
    private void recursiveDiff(
            NestedModuleDataDTO oldNode,
            NestedModuleDataDTO newNode,
            Map<Long, Map<String, RecordDataDTO>> oldModuleRecordMap,
            Map<Long, Map<Long, Map<String, VersionSnapShot>>> currentVersionMap,
            ProjectTableRelationsDTO projectTableRelationsDTO,
            ModuleTreeDTO moduleTreeDTO) {
        if (Objects.isNull(newNode)) {
            return;
        }
        Long moduleId = newNode.getModuleId();

        // 当前模块（作为父模块）的元数据
        ModuleInfo parentModuleInfo = moduleTreeDTO.getModule(moduleId);

        String primaryTable = parentModuleInfo.getPrimaryTable();

        // 该模块涉及的所有物理表名（主表 + 从表）
        Set<String> parentModuleTables =
                parentModuleInfo.getFields().stream()
                        .map(ModuleFieldDTO::getTableName)
                        .collect(Collectors.toSet());

        Set<String> excludeRelateKeys =
                buildExcludeRelateKey(parentModuleTables, projectTableRelationsDTO);

        // 获取旧模块下所有record：business_no -> record节点
        Map<String, RecordDataDTO> oldRecordMap =
                oldModuleRecordMap.getOrDefault(moduleId, Map.of());

        // 处理当前模块的 record 数组
        List<RecordDataDTO> recordArr = newNode.getRecord();
        if (!CollectionUtils.isEmpty(recordArr)) {
            Iterator<RecordDataDTO> recordIt = recordArr.iterator();
            while (recordIt.hasNext()) {
                RecordDataDTO recordItem = recordIt.next();
                Map<String, Object> rowNode = recordItem.getRow();

                if (Objects.nonNull(rowNode)) {
                    // 提取row主键：找到 xxx.id 的字段值
                    String rowBusinessNo = extractRowBusinessNo(rowNode, primaryTable);
                    RecordDataDTO oldRecordItem = oldRecordMap.get(rowBusinessNo);
                    Map<String, Object> oldRow =
                            oldRecordItem != null ? oldRecordItem.getRow() : null;

                    Long rowPk = extractRowPk(rowNode, primaryTable);

                    Map<String, VersionSnapShot> currentChangeMap = new HashMap<>();
                    if (currentVersionMap.containsKey(moduleId)
                            && currentVersionMap.get(moduleId).containsKey(rowPk)) {
                        currentChangeMap = currentVersionMap.get(moduleId).get(rowPk);
                    }

                    // 对比row字段，生成changeList，挂在recordItem（和row平级）
                    Map<String, Map<String, Object>> changeList =
                            buildRowChangeList(
                                    oldRow,
                                    rowNode,
                                    currentChangeMap,
                                    "pre",
                                    "current",
                                    "currentOld",
                                    excludeRelateKeys);
                    if (changeList.size() > 0) {
                        if (changeList.containsKey("preChangeFlag")) {
                            log.debug("该行数据与上个版本对比有变化，标记为改。");
                            recordItem.setChangeType("改");
                            changeList.remove("preChangeFlag");
                        }
                        recordItem.setChangeList(changeList);
                    } else if (Objects.isNull(oldRow) && Objects.nonNull(oldNode)) {
                        recordItem.setChangeType("新");
                    }
                }

                // 递归处理当前recordItem的children
                List<NestedModuleDataDTO> childrenNode = recordItem.getChildren();
                if (!CollectionUtils.isEmpty(childrenNode)) {
                    for (NestedModuleDataDTO childModule : childrenNode) {
                        recursiveDiff(
                                oldNode,
                                childModule,
                                oldModuleRecordMap,
                                currentVersionMap,
                                projectTableRelationsDTO,
                                moduleTreeDTO);
                    }
                }
            }
        }
    }

    private Set<String> buildExcludeRelateKey(
            Set<String> parentModuleTables, ProjectTableRelationsDTO projectTableRelationsDTO) {
        Set<String> excludeKeys = new HashSet<>();
        for (String parentTable : parentModuleTables) {
            List<TableRelationDTO> relations =
                    projectTableRelationsDTO.findRelationsByTable(parentTable);
            for (TableRelationDTO relation : relations) {
                excludeKeys.add(relation.getJoinTable() + "." + relation.getJoinField());
            }
        }
        return excludeKeys;
    }

    /** 构建旧树索引：moduleId -> Map<业务主键, recordNode> */
    private Map<Long, Map<String, RecordDataDTO>> buildOldModuleRecordIndex(
            NestedModuleDataDTO root, ModuleTreeDTO moduleTreeDTO) {
        Map<Long, Map<String, RecordDataDTO>> index = new ConcurrentHashMap<>();
        traverseOldTree(root, index, moduleTreeDTO);
        return index;
    }

    private void traverseOldTree(
            NestedModuleDataDTO node,
            Map<Long, Map<String, RecordDataDTO>> index,
            ModuleTreeDTO moduleTreeDTO) {
        if (Objects.isNull(node)) return;
        Long moduleId = node.getModuleId();

        ModuleInfo moduleInfo = moduleTreeDTO.getModule(moduleId);

        String primaryTable = moduleInfo.getPrimaryTable();
        if (moduleId != null) {
            List<RecordDataDTO> recordArr = node.getRecord();
            if (!CollectionUtils.isEmpty(recordArr)) {
                Map<String, RecordDataDTO> recordMap =
                        index.computeIfAbsent(moduleId, k -> new ConcurrentHashMap<>());
                for (RecordDataDTO recordItem : recordArr) {
                    Map<String, Object> rowNode = recordItem.getRow();
                    // 虚模块时不跳过，继续递归子模块
                    if (rowNode == null && StringUtils.isNotBlank(moduleInfo.getPrimaryTable())) {
                        continue;
                    }
                    String businessNoPk = extractRowBusinessNo(rowNode, primaryTable);
                    if (StringUtils.isNotBlank(businessNoPk)) {
                        recordMap.put(businessNoPk, recordItem);
                    }

                    // 递归children
                    List<NestedModuleDataDTO> childrenNode = recordItem.getChildren();
                    if (!CollectionUtils.isEmpty(childrenNode)) {
                        for (NestedModuleDataDTO child : childrenNode) {
                            traverseOldTree(child, index, moduleTreeDTO);
                        }
                    }
                }
            }
        }
    }

    /** 从row中提取主键：查找key以 .business_no 结尾的字段，例如 info.business_no、basic_info.business_no */
    private String extractRowBusinessNo(Map<String, Object> rowNode, String tableName) {
        if (Objects.isNull(rowNode)) {
            return "";
        }
        Object primaryTableId = rowNode.get(tableName + "." + "business_no");

        if (Objects.nonNull(primaryTableId)) {
            return String.valueOf(primaryTableId);
        } else {
            return "";
        }
    }

    /** 从row中提取主键：查找key以 .id 结尾的字段，例如 info.id、basic_info.id */
    private Long extractRowPk(Map<String, Object> rowNode, String tableName) {
        if (Objects.isNull(rowNode)) {
            return null;
        }
        Object primaryTableId = rowNode.get(tableName + "." + "id");

        if (Objects.nonNull(primaryTableId)) {
            return Long.parseLong(String.valueOf(primaryTableId));
        } else {
            return null;
        }
    }

    /**
     * 对比旧row和新row，生成 changeList
     *
     * @return ObjectNode changeList
     */
    private static Map<String, Map<String, Object>> buildRowChangeList(
            Map<String, Object> oldRow,
            Map<String, Object> newRow,
            Map<String, VersionSnapShot> currentChangeMap,
            String preKey,
            String currentKey,
            String currentOldKey,
            Set<String> excludeRelateKeys) {

        Map<String, Map<String, Object>> changeList = new HashMap<>();
        if (Objects.isNull(newRow)) {
            return changeList;
        }

        boolean preChangeFlag = false;

        Iterator<Map.Entry<String, Object>> newFields = newRow.entrySet().iterator();
        while (newFields.hasNext()) {
            Map.Entry<String, Object> entry = newFields.next();
            String fieldKey = entry.getKey();
            String[] columnName = fieldKey.split("\\.");
            if (columnName.length == 2 && StringUtils.isNotBlank(columnName[1])) {
                // 非业务字段跳过对比
                if (EXCLUDE_FIELDS.stream().anyMatch(columnName[1]::equals)) {
                    continue;
                }
            }

            // 关联外键不参与对比
            if (excludeRelateKeys.contains(fieldKey)) {
                continue;
            }
            Object newVal = entry.getValue();
            Object oldVal = oldRow == null ? null : oldRow.get(fieldKey);
            //            VersionCompareItem.VersionCompareItemBuilder versionCompareItem =
            //                    VersionCompareItem.builder();

            Map<String, Object> versionCompareItem = new HashMap<>();

            boolean addVersionItem = false;

            // 值相同跳过，只记录变更字段
            if (Objects.nonNull(oldRow) && !isEqual(newVal, oldVal)) {
                versionCompareItem.put(preKey, oldVal);
                versionCompareItem.put(currentKey, newVal);
                addVersionItem = true;
                preChangeFlag = true;
            }
            if (Objects.nonNull(currentChangeMap.get(fieldKey))) {
                VersionSnapShot changeObj = currentChangeMap.get(fieldKey);

                try {
                    if (Objects.nonNull(changeObj)) {

                        if (Objects.nonNull(changeObj.getSecondSnapshot())) {
                            if (isEqual(newVal, changeObj.getSecondSnapshot())) {
                                if (!isEqual(newVal, changeObj.getFirstSnapshot())) {
                                    if (isJsonStr(changeObj.getFirstSnapshot())) {
                                        Object currentOld =
                                                OBJECT_MAPPER.readTree(
                                                        changeObj.getFirstSnapshot());
                                        versionCompareItem.put(currentOldKey, currentOld);
                                    } else {
                                        versionCompareItem.put(
                                                currentOldKey, changeObj.getFirstSnapshot());
                                    }
                                    versionCompareItem.put(currentKey, newVal);
                                    addVersionItem = true;
                                }
                            } else {
                                if (isJsonStr(changeObj.getSecondSnapshot())) {
                                    Object currentOld =
                                            OBJECT_MAPPER.readTree(changeObj.getSecondSnapshot());
                                    versionCompareItem.put(currentOldKey, currentOld);
                                } else {
                                    versionCompareItem.put(
                                            currentOldKey, changeObj.getSecondSnapshot());
                                }
                                versionCompareItem.put(currentKey, newVal);
                                addVersionItem = true;
                            }
                        } else {
                            if (!isEqual(newVal, changeObj.getFirstSnapshot())) {
                                if (isJsonStr(changeObj.getFirstSnapshot())) {
                                    Object currentOld =
                                            OBJECT_MAPPER.readTree(changeObj.getFirstSnapshot());
                                    versionCompareItem.put(currentOldKey, currentOld);
                                } else {
                                    versionCompareItem.put(
                                            currentOldKey, changeObj.getFirstSnapshot());
                                }
                                versionCompareItem.put(currentKey, newVal);
                                addVersionItem = true;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("版本内对比构造数据失败");
                }
            }

            // 构造 {pre:旧值, currentOld:旧值, current:新值}
            if (addVersionItem) {
                changeList.put(entry.getKey(), versionCompareItem);
            }
        }
        if (preChangeFlag) {
            changeList.put("preChangeFlag", new HashMap<>());
        }
        return changeList;
    }

    /**
     * 对外入口方法
     *
     * @param source 源对象（决定类型规则）
     * @param target 待对比目标对象
     * @return true=相等，false=不相等
     */
    public static boolean isEqual(Object source, Object target) {
        // 1. 两边都null
        if (source == null && target == null) {
            return true;
        }
        // 2. 其中一个null
        if (source == null || target == null) {
            return false;
        }

        // ========= 任意一方是JSON字符串，解析为JsonNode对比 =========
        boolean sourceIsJsonStr = source instanceof String && isJsonStr((String) source);
        boolean targetIsJsonStr = target instanceof String && isJsonStr((String) target);
        if (sourceIsJsonStr || targetIsJsonStr) {
            return compareAnyJsonSide(source, target);
        }

        Class<?> sourceCls = source.getClass();

        // ---------------------- Long ----------------------
        if (Long.class.equals(sourceCls)) {
            return compareLong((Long) source, target);
        }
        // ---------------------- BigInteger ----------------------
        if (BigInteger.class.equals(sourceCls)) {
            return compareBigInteger((BigInteger) source, target);
        }

        // ---------------------- Integer ----------------------
        if (Integer.class.equals(sourceCls)) {
            return compareInteger((Integer) source, target);
        }
        // BigDecimal
        if (BigDecimal.class.equals(sourceCls)) {
            return compareBigDecimal((BigDecimal) source, target);
        }

        // ---------------------- String ----------------------
        if (String.class.equals(sourceCls)) {
            String sourceStr = (String) source;
            // 判断源字符串是否为JSON
            if (isJsonStr(sourceStr)) {
                return compareJsonStr(sourceStr, target);
            } else {
                // 普通字符串，转字符串equals
                return Objects.equals(sourceStr, target.toString());
            }
        }
        // ---------------------- Date ----------------------
        if (java.sql.Date.class.equals(sourceCls)) {
            return compareDate((Date) source, target);
        }
        // ---------------------- LocalDateTime ----------------------
        if (LocalDateTime.class.equals(sourceCls)) {
            return compareLocalDateTime((LocalDateTime) source, target);
        }

        // ---------------------- 自定义对象类型：反射深度对比 ----------------------
        return compareObject(source, target);
    }

    // ===================== BigInteger对比 =====================
    private static boolean compareBigInteger(BigInteger source, Object target) {
        BigInteger targetBigInt;
        if (target instanceof BigInteger) {
            targetBigInt = (BigInteger) target;
        } else if (target instanceof BigDecimal) {
            // BigDecimal转BigInteger（取整数部分）
            targetBigInt = ((BigDecimal) target).toBigInteger();
        } else if (target instanceof Number) {
            targetBigInt = BigInteger.valueOf(((Number) target).longValue());
        } else if (target instanceof String) {
            String str = ((String) target).trim();
            try {
                targetBigInt = new BigInteger(str);
            } catch (NumberFormatException e) {
                return false;
            }
        } else {
            return false;
        }
        // BigInteger使用equals做数值相等判断
        return source.equals(targetBigInt);
    }

    // ========== Long对比：目标转Long再比较 ==========
    private static boolean compareLong(Long source, Object target) {
        Long targetLong;
        if (target instanceof Number) {
            targetLong = ((Number) target).longValue();
        } else if (target instanceof String) {
            try {
                targetLong = Long.parseLong(((String) target).trim());
            } catch (NumberFormatException e) {
                return false;
            }
        } else {
            return false;
        }
        return Objects.equals(source, targetLong);
    }

    // ========== Integer对比：目标转Integer ==========
    private static boolean compareInteger(Integer source, Object target) {
        Integer targetInt;
        if (target instanceof Number) {
            targetInt = ((Number) target).intValue();
        } else if (target instanceof String) {
            try {
                targetInt = Integer.parseInt(((String) target).trim());
            } catch (NumberFormatException e) {
                return false;
            }
        } else {
            return false;
        }
        return Objects.equals(source, targetInt);
    }

    // ===================== BigDecimal对比 =====================
    private static boolean compareBigDecimal(BigDecimal source, Object target) {
        BigDecimal targetDec;
        if (target instanceof BigDecimal) {
            targetDec = (BigDecimal) target;
        } else if (target instanceof Number) {
            targetDec = new BigDecimal(target.toString());
        } else if (target instanceof String) {
            String str = ((String) target).trim();
            try {
                targetDec = new BigDecimal(str);
            } catch (NumberFormatException e) {
                return false;
            }
        } else {
            return false;
        }
        // 使用compareTo，解决 2.0 和 2.00 equals不等问题
        return source.compareTo(targetDec) == 0;
    }

    // ===================== JSON处理：任意一侧是JSON字符串就解析对比 =====================
    private static boolean compareAnyJsonSide(Object source, Object target) {
        try {
            if (source instanceof String && isJsonStr((String) source)) {
                source = OBJECT_MAPPER.readTree((String) source);
                removeFields((JsonNode) source);
            }

            if (target instanceof String && isJsonStr((String) target)) {
                target = OBJECT_MAPPER.readTree((String) target);
                removeFields((JsonNode) target);
            }

            return source.equals(target);
        } catch (Exception e) {
            return false;
        }
    }

    // ========== JSON字符串对比：源是json串，把目标解析成json对象对比 ==========
    private static boolean compareJsonStr(String sourceJson, Object target) {
        try {
            JsonNode sourceObj = OBJECT_MAPPER.readTree(sourceJson);
            removeFields(sourceObj);
            Object targetObj;
            if (target instanceof String) {
                targetObj = OBJECT_MAPPER.readTree((String) target);
                removeFields((JsonNode) targetObj);
            } else {
                targetObj = target;
            }

            // Jackson自带深度equals
            return sourceObj.equals(OBJECT_MAPPER.valueToTree(targetObj));
        } catch (Exception e) {
            return false;
        }
    }

    // 判断字符串是否是JSON
    private static boolean isJsonStr(String str) {
        if (str == null || str.isBlank()) {
            return false;
        }
        String trim = str.trim();
        return (trim.startsWith("{") && trim.endsWith("}"))
                || (trim.startsWith("[") && trim.endsWith("]"));
    }

    // ========== Date对比：目标转Date，按毫秒比较 ==========
    private static boolean compareDate(Date sourceDate, Object target) {
        Date targetDate;
        if (target instanceof Date) {
            targetDate = (Date) target;
        } else if (target instanceof String) {
            try {
                targetDate = DATE_FORMAT.parse(((String) target).trim());
            } catch (ParseException e) {
                return false;
            }
        } else if (target instanceof Number) {
            targetDate = new Date(((Number) target).longValue());
        } else {
            return false;
        }
        return sourceDate.getTime() == targetDate.getTime();
    }

    // ========== LocalDateTime对比 ==========
    private static boolean compareLocalDateTime(LocalDateTime sourceLdt, Object target) {
        Date sourceDate = Date.from(sourceLdt.atZone(ZoneId.systemDefault()).toInstant());
        return compareDate(sourceDate, target);
    }

    // ========== 普通对象：反射字段深度对比 ==========
    private static boolean compareObject(Object source, Object target) {
        Class<?> sourceCls = source.getClass();
        Class<?> targetCls = target.getClass();
        // 类型不一致直接false，如需支持自动类型转换可注释这行
        if (!sourceCls.equals(targetCls)) {
            return false;
        }

        return Objects.equals(source, target);
    }

    public void moduleTreeDataMerge(
            NestedModuleDataDTO oldTree,
            Map<String, Map<String, Map<String, List<Map<String, Object>>>>> currentVersionMap,
            ModuleTreeDTO moduleTreeDTO,
            Long rootModuleId,
            ProjectTableRelationsDTO projectTableRelationsDTO) {
        recursiveMergeData(
                oldTree, currentVersionMap, moduleTreeDTO, rootModuleId, projectTableRelationsDTO);
    }

    /**
     * 递归合并数据：将当前版本的变更数据（currentVersionMap）合并进旧版本树（oldTree）
     *
     * <p>处理逻辑：
     *
     * <ol>
     *   <li>对当前模块的每一行 row 数据，按“update_data”中该行的变更（action 为 UPDATE/INSERT/DELETE）打上 改/新/删
     *       标记，并把变更内容合并回 row，UPDATE 时生成 changeList；
     *   <li>对子模块，按“insert_data”中以父行主键分组的新增数据，构造 changeType=新 的 RecordDataDTO 追加到子模块 record
     *       数组（children 依据模块树配置补空占位）；
     *   <li>递归处理子模块，逐层向下合并。
     * </ol>
     *
     * @param oldTree 旧版本json树（原地合并变更数据）
     * @param currentVersionMap 当前版本变更数据，结构：表名 -&gt; 数据类型(update_data/insert_data) -&gt; 行主键 -&gt;
     *     变更记录列表
     * @param moduleTreeDTO 模块树元数据（模块信息、字段、父子关系）
     * @param parentPrimaryTablerowId 上一层模块的行主键ID（虚模块透传使用）
     */
    private void recursiveMergeData(
            NestedModuleDataDTO oldTree,
            Map<String, Map<String, Map<String, List<Map<String, Object>>>>> currentVersionMap,
            ModuleTreeDTO moduleTreeDTO,
            Long parentPrimaryTablerowId,
            ProjectTableRelationsDTO projectTableRelationsDTO) {
        if (Objects.isNull(oldTree)) {
            return;
        }
        Long moduleId = oldTree.getModuleId();

        // 当前模块（作为父模块）的元数据
        ModuleInfo parentModuleInfo = moduleTreeDTO.getModule(moduleId);

        // 该模块涉及的所有物理表名（主表 + 从表）
        Set<String> parentModuleTables =
                parentModuleInfo.getFields().stream()
                        .map(ModuleFieldDTO::getTableName)
                        .collect(Collectors.toSet());

        Set<String> excludeRelateKeys =
                buildExcludeRelateKey(parentModuleTables, projectTableRelationsDTO);

        // 主表名，用于从 row 中提取行主键（xxx.id）
        String primaryTable = parentModuleInfo.getPrimaryTable();

        // 处理当前模块的 record 数组
        List<RecordDataDTO> recordArr = oldTree.getRecord();
        if (!CollectionUtils.isEmpty(recordArr)) {
            Iterator<RecordDataDTO> recordIt = recordArr.iterator();
            while (recordIt.hasNext()) {
                RecordDataDTO recordItem = recordIt.next();
                Map<String, Object> rowRawData = recordItem.getRow();

                Long primaryTablerowId;

                Map<String, Map<String, Object>> changeList = new HashMap<>();

                if (Objects.nonNull(rowRawData)) {
                    // 提取row主键：找到 xxx.id 的字段值
                    primaryTablerowId = extractRowPk(rowRawData, primaryTable);

                    // 搜集模块行数据，主表和从表数据进行拼接：按表名遍历，命中当前行的 UPDATE/INSERT/DELETE 变更
                    for (String tableName : parentModuleTables) {
                        Long tableRowId = extractRowPk(rowRawData, tableName);

                        List<Map<String, Object>> currentChangeData =
                                Optional.ofNullable(currentVersionMap.get(tableName))
                                        .map(m -> m.get("update_data"))
                                        .map(m -> m.get(String.valueOf(primaryTablerowId)))
                                        .orElse(Collections.emptyList());

                        // 当前行数据更改
                        if (!CollectionUtils.isEmpty(currentChangeData)) {
                            Map<String, Object> rowChangeData = currentChangeData.get(0);

                            // 同一张表在多个模块使用，用当前数据的source_id筛选，取出对应的正确数据
                            //                            Map<String, Object> rowChangeData =
                            // currentChangeData.stream()
                            //                                    .filter(m -> m.get("source_id") ==
                            // null || Objects.equals(tableRowId,
                            // Long.parseLong(String.valueOf(m.get("source_id")))))
                            //                                    .findFirst()
                            //                                    .orElse(null);

                            String tableNamePrefix =
                                    String.valueOf(rowChangeData.get("table_name"));

                            String action = String.valueOf(rowChangeData.get("action"));

                            Map<String, Object> changeContentMap = new HashMap<>();

                            Map<String, Object> originChangeContentMap = new HashMap<>();

                            Object jsonObject = rowChangeData.get("change_content");
                            if (jsonObject instanceof JSON jooqJson) {
                                String jsonStr = jooqJson.data();
                                if (StringUtils.isNotBlank(jsonStr)) {
                                    try {
                                        originChangeContentMap =
                                                OBJECT_MAPPER.readValue(jsonStr, Map.class);
                                    } catch (JsonProcessingException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                            }

                            // 将变更内容字段加上表名前缀（table_name.field），与 row 的 key 结构对齐

                            changeContentMap =
                                    originChangeContentMap.entrySet().stream()
                                            .collect(
                                                    HashMap::new,
                                                    (map, item) ->
                                                            map.put(
                                                                    tableNamePrefix
                                                                            + "."
                                                                            + item.getKey(),
                                                                    item.getValue()),
                                                    HashMap::putAll);

                            Map<String, VersionSnapShot> currentChangeMap = new HashMap<>();

                            if ("UPDATE".equals(action)) {
                                // 对比row字段，生成changeList，挂在recordItem（和row平级）
                                Map<String, Map<String, Object>> rowChangeMap =
                                        buildRowChangeList(
                                                rowRawData,
                                                changeContentMap,
                                                currentChangeMap,
                                                "original",
                                                "change",
                                                "",
                                                excludeRelateKeys);
                                rowChangeMap.remove("preChangeFlag");
                                changeList.putAll(rowChangeMap);
                                recordItem.setChangeType("改");
                            } else if ("INSERT".equals(action)) {
                                recordItem.setChangeType("新");
                            } else {
                                // DELETE 等其他动作统一标记为“删”
                                recordItem.setChangeType("删");
                            }

                            // 将变更后的字段值覆盖回原始行数据，完成原地合并
                            rowRawData.putAll(changeContentMap);
                        }
                    }

                    if (!changeList.isEmpty()) {
                        recordItem.setChangeList(changeList);
                    }
                } else {
                    // row 为空（如虚模块占位节点），主键置 0 避免后续按 null 分组匹配
                    primaryTablerowId = 0L;
                }

                // 递归处理当前recordItem的children
                List<NestedModuleDataDTO> childrenNode = recordItem.getChildren();

                if (!CollectionUtils.isEmpty(childrenNode)) {
                    Long parentRowId;

                    boolean virtualModule = false;
                    if (Objects.isNull(rowRawData) && CollectionUtils.isEmpty(parentModuleTables)) {
                        // 虚模块：自身无行数据也无物理表，仅作为层级占位
                        log.debug(
                                "模块id:{},模块名称:{}是虚模块", moduleId, parentModuleInfo.getModuleName());

                        virtualModule = true;
                        // 虚模块时，parentRowId使用上一层模块的主键ID,该值由递归传入
                        parentRowId = parentPrimaryTablerowId;
                    } else {
                        parentRowId = primaryTablerowId;
                    }
                    for (NestedModuleDataDTO childModule : childrenNode) {
                        ModuleInfo childModuleInfo =
                                moduleTreeDTO.getModule(childModule.getModuleId());

                        Map<String, Map<String, Object>> childTableChangeData = new HashMap<>();

                        Set<String> childModuleTables =
                                childModuleInfo.getFields().stream()
                                        .map(ModuleFieldDTO::getTableName)
                                        .collect(Collectors.toSet());

                        // 搜集模块行数据，主表和从表数据进行拼接：按父行主键取出子表的新增数据，按 node_id 分组合并为完整行
                        for (String childTableName : childModuleTables) {
                            List<Map<String, Object>> childrenAddData =
                                    Optional.ofNullable(currentVersionMap.get(childTableName))
                                            .map(m -> m.get("insert_data"))
                                            .map(m -> m.get(String.valueOf(parentRowId)))
                                            .orElse(Collections.emptyList());

                            childrenAddData.forEach(
                                    ele -> {
                                        Object jsonObject = ele.get("change_content");

                                        Map<String, Object> originChangeContentMap =
                                                new HashMap<>();
                                        if (jsonObject instanceof JSON jooqJson) {
                                            String jsonStr = jooqJson.data();
                                            if (StringUtils.isNotBlank(jsonStr)) {
                                                try {
                                                    originChangeContentMap =
                                                            OBJECT_MAPPER.readValue(
                                                                    jsonStr, Map.class);
                                                } catch (JsonProcessingException e) {
                                                    throw new RuntimeException(e);
                                                }
                                            }
                                        }

                                        String nodeId = String.valueOf(ele.get("node_id"));

                                        Map<String, Object> prefixAddChangeContentMap =
                                                new HashMap<>();

                                        prefixAddChangeContentMap =
                                                originChangeContentMap.entrySet().stream()
                                                        .collect(
                                                                HashMap::new,
                                                                (map, item) ->
                                                                        map.put(
                                                                                childTableName
                                                                                        + "."
                                                                                        + item
                                                                                                .getKey(),
                                                                                item.getValue()),
                                                                HashMap::putAll);

                                        childTableChangeData
                                                .computeIfAbsent(nodeId, k -> new HashMap<>())
                                                .putAll(prefixAddChangeContentMap);
                                    });
                        }

                        // 将合并出的新增行构造为 changeType=新 的记录追加到子模块，
                        // children 依据模块树配置的子模块ID补空占位节点
                        childModule
                                .getRecord()
                                .addAll(
                                        childTableChangeData.values().stream()
                                                .map(
                                                        stringObjectMap ->
                                                                RecordDataDTO.builder()
                                                                        .row((stringObjectMap))
                                                                        .changeType("新")
                                                                        .children(
                                                                                moduleTreeDTO
                                                                                        .getChildModuleIds(
                                                                                                childModule
                                                                                                        .getModuleId())
                                                                                        .stream()
                                                                                        .map(
                                                                                                id ->
                                                                                                        NestedModuleDataDTO
                                                                                                                .builder()
                                                                                                                .moduleId(
                                                                                                                        id)
                                                                                                                .record(
                                                                                                                        new ArrayList<>())
                                                                                                                .build())
                                                                                        .collect(
                                                                                                Collectors
                                                                                                        .toList()))
                                                                        .build())
                                                .toList());
                        // 递归处理子模块，传入当前模块行主键作为其父级主键
                        recursiveMergeData(
                                childModule,
                                currentVersionMap,
                                moduleTreeDTO,
                                primaryTablerowId,
                                projectTableRelationsDTO);
                    }
                }
            }
        }
    }

    /** 自动将 JSON 字符串或 jOOQ JSON 对象转换为结构化的 List/Map，保持返回值纯净 */
    private JsonNode parseIfJson(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof JSON jooqJson) {
            String jsonStr = jooqJson.data();
            if (jsonStr == null || jsonStr.isBlank()) {
                return null;
            }
            try {
                return OBJECT_MAPPER.readTree(jsonStr);
            } catch (Exception e) {
                return null;
            }
        }
        if (val instanceof String str) {
            String trimmed = str.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                    || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                try {
                    return OBJECT_MAPPER.readTree(trimmed);
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 给JsonNode第一层key增加统一前缀
     *
     * @param node 源节点
     * @param prefix 前缀，如 "prefix_"
     * @return 处理后的新JsonNode
     */
    public static JsonNode addFirstLevelKeyPrefix(JsonNode node, String prefix) {
        if (node == null || prefix == null || prefix.isEmpty()) {
            return node;
        }
        JsonNodeFactory factory = JsonNodeFactory.instance;

        // 1. 对象节点：第一层key加前缀
        if (node.isObject()) {
            ObjectNode source = (ObjectNode) node;
            ObjectNode target = factory.objectNode();
            source.fields()
                    .forEachRemaining(
                            entry -> {
                                String newKey = prefix + entry.getKey();
                                target.set(newKey, entry.getValue());
                            });
            return target;
        }

        // 2. 数组节点：遍历数组内每个元素，元素对象才加前缀
        if (node.isArray()) {
            ArrayNode sourceArray = (ArrayNode) node;
            ArrayNode targetArray = factory.arrayNode();
            for (JsonNode item : sourceArray) {
                targetArray.add(addFirstLevelKeyPrefix(item, prefix));
            }
            return targetArray;
        }

        // 3. 值节点（字符串、数字、布尔等）直接返回
        return node;
    }

    /** 递归移除节点中的指定字段（如 id、pid、relation_no） */
    private static void removeFields(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node instanceof ObjectNode objectNode) {
            for (String field : REMOVED_FIELDS) {
                objectNode.remove(field);
            }
            for (Iterator<JsonNode> it = objectNode.elements(); it.hasNext(); ) {
                removeFields(it.next());
            }
        } else if (node instanceof ArrayNode arrayNode) {
            for (JsonNode item : arrayNode) {
                removeFields(item);
            }
        }
    }
}
