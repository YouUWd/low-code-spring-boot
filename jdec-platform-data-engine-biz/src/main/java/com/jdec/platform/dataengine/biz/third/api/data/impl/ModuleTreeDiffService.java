package com.jdec.platform.dataengine.biz.third.api.data.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.api.dto.model.SysModuleInfo;
import com.jdec.platform.dataengine.biz.config.service.DataModuleConfigService;
import com.jdec.platform.dataengine.biz.dto.VersionSnapShot;
import com.jdec.platform.shared.context.AppContext;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/** 模块树字段对比数据生成类 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModuleTreeDiffService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private final DataModuleConfigService dataModuleConfigService;

    /**
     * 入口：对比两棵模块树，【新树】原地增加 changeList
     *
     * @param oldTree 旧版本json树（只读）
     * @param newTree 新版本json树（原地增加changeList）
     */
    public void diffModuleTree(
            NestedModuleDataDTO oldTree,
            NestedModuleDataDTO newTree,
            Map<Long, Map<Long, Map<String, VersionSnapShot>>> currentVersionMap) {
        // 构建旧树 moduleId -> recordMap(业务id -> recordNode)
        Map<Long, Map<String, RecordDataDTO>> oldModuleRecordMap =
                buildOldModuleRecordIndex(oldTree);
        recursiveDiff(oldTree, newTree, oldModuleRecordMap, currentVersionMap);
    }

    /** 递归处理模块节点 */
    private void recursiveDiff(
            NestedModuleDataDTO oldNode,
            NestedModuleDataDTO newNode,
            Map<Long, Map<String, RecordDataDTO>> oldModuleRecordMap,
            Map<Long, Map<Long, Map<String, VersionSnapShot>>> currentVersionMap) {
        if (Objects.isNull(newNode)) {
            return;
        }
        Long moduleId = newNode.getModuleId();

        SysModuleInfo sysModuleInfo =
                dataModuleConfigService.getModuleCompleteById(
                        AppContext.getProjectNo(), AppContext.getSubjectId(), moduleId);

        String primaryTable = sysModuleInfo.getPrimaryTable();

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
                if (Objects.isNull(rowNode)) {
                    continue;
                }
                // 提取row主键：找到 xxx.id 的字段值
                String rowBusinessNo = extractRowBusinessNo(rowNode, primaryTable);
                RecordDataDTO oldRecordItem = oldRecordMap.get(rowBusinessNo);
                Map<String, Object> oldRow = oldRecordItem != null ? oldRecordItem.getRow() : null;

                Long rowPk = extractRowPk(rowNode, primaryTable);

                Map<String, VersionSnapShot> currentChangeMap = new HashMap<>();
                if (currentVersionMap.containsKey(moduleId)
                        && currentVersionMap.get(moduleId).containsKey(rowPk)) {
                    currentChangeMap = currentVersionMap.get(moduleId).get(rowPk);
                }

                // 对比row字段，生成changeList，挂在recordItem（和row平级）
                Map<String, Map<String, Object>> changeList =
                        buildRowChangeList(oldRow, rowNode, currentChangeMap);
                if (changeList.size() > 0) {
                    recordItem.setChangeList(changeList);
                    recordItem.setChangeType("改");
                } else if (Objects.isNull(oldRow) && Objects.nonNull(oldNode)) {
                    recordItem.setChangeType("新");
                }

                // 递归处理当前recordItem的children
                List<NestedModuleDataDTO> childrenNode = recordItem.getChildren();
                if (!CollectionUtils.isEmpty(childrenNode)) {
                    for (NestedModuleDataDTO childModule : childrenNode) {
                        recursiveDiff(oldNode, childModule, oldModuleRecordMap, currentVersionMap);
                    }
                }
            }
        }
    }

    /** 构建旧树索引：moduleId -> Map<业务主键, recordNode> */
    private Map<Long, Map<String, RecordDataDTO>> buildOldModuleRecordIndex(
            NestedModuleDataDTO root) {
        Map<Long, Map<String, RecordDataDTO>> index = new ConcurrentHashMap<>();
        traverseOldTree(root, index);
        return index;
    }

    private void traverseOldTree(
            NestedModuleDataDTO node, Map<Long, Map<String, RecordDataDTO>> index) {
        if (Objects.isNull(node)) return;
        Long moduleId = node.getModuleId();
        SysModuleInfo sysModuleInfo =
                dataModuleConfigService.getModuleCompleteById(
                        AppContext.getProjectNo(), AppContext.getSubjectId(), moduleId);
        String primaryTable = sysModuleInfo.getPrimaryTable();
        if (moduleId != null) {
            List<RecordDataDTO> recordArr = node.getRecord();
            if (!CollectionUtils.isEmpty(recordArr)) {
                Map<String, RecordDataDTO> recordMap =
                        index.computeIfAbsent(moduleId, k -> new ConcurrentHashMap<>());
                for (RecordDataDTO recordItem : recordArr) {
                    Map<String, Object> rowNode = recordItem.getRow();
                    if (rowNode == null) continue;
                    String businessNoPk = extractRowBusinessNo(rowNode, primaryTable);
                    if (StringUtils.isNotBlank(businessNoPk)) {
                        recordMap.put(businessNoPk, recordItem);
                    }

                    // 递归children
                    List<NestedModuleDataDTO> childrenNode = recordItem.getChildren();
                    if (!CollectionUtils.isEmpty(childrenNode)) {
                        for (NestedModuleDataDTO child : childrenNode) {
                            traverseOldTree(child, index);
                        }
                    }
                }
            }
        }
    }

    /** 从row中提取主键：查找key以 .business_no 结尾的字段，例如 info.business_no、basic_info.business_no */
    private String extractRowBusinessNo(Map<String, Object> rowNode, String tableName) {
        Object primaryTableId = rowNode.get(tableName + "." + "business_no");

        if (Objects.nonNull(primaryTableId)) {
            return String.valueOf(primaryTableId);
        } else {
            return "";
        }
    }

    /** 从row中提取主键：查找key以 .id 结尾的字段，例如 info.id、basic_info.id */
    private Long extractRowPk(Map<String, Object> rowNode, String tableName) {
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
            Map<String, VersionSnapShot> currentChangeMap) {
        Map<String, Map<String, Object>> changeList = new HashMap<>();
        if (Objects.isNull(newRow)) {
            return changeList;
        }

        Iterator<Map.Entry<String, Object>> newFields = newRow.entrySet().iterator();
        while (newFields.hasNext()) {
            Map.Entry<String, Object> entry = newFields.next();
            String fieldKey = entry.getKey();
            Object newVal = entry.getValue();
            Object oldVal = oldRow == null ? null : oldRow.get(fieldKey);
            //            VersionCompareItem.VersionCompareItemBuilder versionCompareItem =
            //                    VersionCompareItem.builder();

            Map<String, Object> versionCompareItem = new HashMap<>();

            boolean addVersionItem = false;

            // 值相同跳过，只记录变更字段
            if (Objects.nonNull(oldRow) && !isEqual(newVal, oldVal)) {
                versionCompareItem.put("pre", oldVal);
                versionCompareItem.put("current", newVal);
                addVersionItem = true;
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
                                        versionCompareItem.put("currentOld", currentOld);
                                    } else {
                                        versionCompareItem.put(
                                                "currentOld", changeObj.getFirstSnapshot());
                                    }
                                    versionCompareItem.put("current", newVal);
                                    addVersionItem = true;
                                }
                            } else {
                                if (isJsonStr(changeObj.getSecondSnapshot())) {
                                    Object currentOld =
                                            OBJECT_MAPPER.readTree(changeObj.getSecondSnapshot());
                                    versionCompareItem.put("currentOld", currentOld);
                                } else {
                                    versionCompareItem.put(
                                            "currentOld", changeObj.getSecondSnapshot());
                                }
                                versionCompareItem.put("current", newVal);
                                addVersionItem = true;
                            }
                        } else {
                            if (!isEqual(newVal, changeObj.getFirstSnapshot())) {
                                if (isJsonStr(changeObj.getFirstSnapshot())) {
                                    Object currentOld =
                                            OBJECT_MAPPER.readTree(changeObj.getFirstSnapshot());
                                    versionCompareItem.put("currentOld", currentOld);
                                } else {
                                    versionCompareItem.put(
                                            "currentOld", changeObj.getFirstSnapshot());
                                }
                                versionCompareItem.put("current", newVal);
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
        return changeList;
    }

    /**
     * 对外入口方法
     *
     * @param source 源对象（决定类型规则）
     * @param target 待对比目标对象
     * @return true=相等，false=不相等
     */
    private static boolean isEqual(Object source, Object target) {
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
            }

            if (target instanceof String && isJsonStr((String) target)) {
                target = OBJECT_MAPPER.readTree((String) target);
            }

            return source.equals(target);
        } catch (Exception e) {
            return false;
        }
    }

    // ========== JSON字符串对比：源是json串，把目标解析成json对象对比 ==========
    private static boolean compareJsonStr(String sourceJson, Object target) {
        try {
            Object sourceObj = OBJECT_MAPPER.readValue(sourceJson, Object.class);
            Object targetObj;
            if (target instanceof String) {
                targetObj = OBJECT_MAPPER.readValue((String) target, Object.class);
            } else {
                targetObj = target;
            }
            // Jackson自带深度equals
            return OBJECT_MAPPER
                    .valueToTree(sourceObj)
                    .equals(OBJECT_MAPPER.valueToTree(targetObj));
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

        /**
         * Field[] fields = sourceCls.getDeclaredFields(); for (Field field : fields) {
         * field.setAccessible(true); Object sourceFieldVal; Object targetFieldVal; try {
         * sourceFieldVal = field.get(source); targetFieldVal = field.get(target); } catch
         * (IllegalAccessException e) { continue; } // 递归调用本方法嵌套对比 if (!isEqual(sourceFieldVal,
         * targetFieldVal)) { return false; } }
         */
        return Objects.equals(source, target);
    }
}
