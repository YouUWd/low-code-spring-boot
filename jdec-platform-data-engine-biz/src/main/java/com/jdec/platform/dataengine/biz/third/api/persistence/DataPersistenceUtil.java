package com.jdec.platform.dataengine.biz.third.api.persistence;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.api.dto.model.FieldChangeDTO;
import com.jdec.platform.dataengine.api.dto.model.ModuleFieldDTO;
import com.jdec.platform.dataengine.api.dto.model.RowChangeDTO;
import com.jdec.platform.dataengine.biz.constants.CommonConstant;
import com.jdec.platform.dataengine.biz.util.JsonUtil;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jooq.JSON;
import org.jooq.Record;

public class DataPersistenceUtil {

    public static Long getTableRowId(Map<String, Object> record) {
        Object idObj = record.get(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD);
        Long id = idObj instanceof Number num ? num.longValue() : null;
        return id;
    }

    public static Boolean getTableRowDeletedField(Map<String, Object> record) {
        Object idObj = record.get(CommonConstant.TABLE_DEFAULT_DELETED_FIELD);
        Boolean flag = idObj instanceof Boolean bool ? bool : null;
        return flag;
    }

    public static Map<String, Object> getTableRowMapFromWideRow(
            Map<String, Object> wideRowMap, String tableName) {
        Map<String, Object> rowMap = new HashMap<>();
        wideRowMap.forEach(
                (key, value) -> {
                    if (key.startsWith(tableName)) {
                        rowMap.put(key.substring(tableName.length() + 1), value);
                    }
                });
        return rowMap;
    }

    public static List<Map<String, Object>> getTableRecordListMap(
            Map<String, Object> recordMap, String tableName) {
        return (List<Map<String, Object>>) recordMap.get(tableName);
    }

    public static Map<String, Object> getTableRecordFromCombinedMap(
            Map<String, Object> combinedMap, String tableName) {
        Map<String, Object> dataMap = (Map<String, Object>) combinedMap.get(tableName);
        Map<String, Object> tableRecord = (Map<String, Object>) dataMap.get("data");
        return tableRecord;
    }

    public static List<Map<String, Object>> getTableRecordListFromCombinedMap(
            Map<String, Object> combinedMap, String tableName) {
        List<Map<String, Object>> dataMapList =
                (List<Map<String, Object>>) combinedMap.get(tableName);

        //        List<Map<String, Object>> result = new ArrayList<>();
        //        for (Map<String, Object> dataMap : dataMapList) {
        //            Map<String, Object> tableRecord = (Map<String, Object>) dataMap.get("data");
        //            result.add(tableRecord);
        //        }
        return dataMapList;
    }

    public static Map<String, Object> fillTableRecordIdInCombinedMap(
            Map<String, Object> combinedMap, String tableName, Object id) {
        Map<String, Object> dataMap = (Map<String, Object>) combinedMap.get(tableName);
        Map<String, Object> tableRecord = (Map<String, Object>) dataMap.get("data");
        tableRecord.put("id", id);
        return tableRecord;
    }

    public static Map<String, Object> fillTableRecordFieldInRecordMap(
            Map<String, Object> recordMap, String field, Object id) {
        recordMap.put(field, id);
        return recordMap;
    }

    public static Map<String, Object> fillTableRowFieldInWideRow(
            Map<String, Object> wideRowMap, String table, String field, Object id) {
        wideRowMap.put(table + "." + field, id);
        return wideRowMap;
    }

    public static boolean isExistFiledInInWideRow(
            Map<String, Object> wideRowMap, String table, String field) {
        return wideRowMap.containsKey(table + "." + field)
                && wideRowMap.get(table + "." + field) != null;
    }

    public static boolean isEmpty(Map<String, Object> rowMap) {
        return CollectionUtil.isEmpty(rowMap);
    }

    public static boolean isOnlyHasId(Map<String, Object> rowMap) {
        return CollectionUtil.isNotEmpty(rowMap)
                && rowMap.size() == 1
                && rowMap.containsKey(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD);
    }

    public static Map<String, Object> fillTableRecordChangeInCombinedMap(
            Map<String, Object> combinedMap, String tableName, Object dataChange) {
        Map<String, Object> dataMap = (Map<String, Object>) combinedMap.get(tableName);
        dataMap.put("change", dataChange);

        return dataMap;
    }

    public static Map<String, Object> fillTableRecordChangeInRecordMap(
            Map<String, Object> recordMap, Object dataChange) {
        recordMap.put("change", dataChange);
        return recordMap;
    }

    public static Map<String, Object> convertRecordToMap(Record r) {
        Map<String, Object> map = new HashMap<>();
        try {
            if (r != null && r.fields() != null) {
                for (org.jooq.Field field : r.fields()) {
                    Object val = r.get(field);
                    if (val instanceof JSON v) {
                        val = JsonUtil.json2Obj(v.data(), Object.class);
                        map.put(field.getName(), val);
                    } else {
                        map.put(field.getName(), val);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return map;
    }

    public static RowChangeDTO convertUpdateRecordToRowChangeDTO(
            Map<String, Object> r,
            Map<String, Object> recordMap,
            String table,
            Map<String, ModuleFieldDTO> fieldMap,
            List<String> uniqueFields,
            Boolean isDelete) {

        if (CollectionUtil.isEmpty(recordMap)) {
            return null;
        }
        List<FieldChangeDTO> fieldChangeDTOList = new ArrayList<>();
        for (String field : recordMap.keySet()) {
            Object old = r.get(field);
            Object newer = recordMap.get(field);
            String displayName =
                    fieldMap.get(field) != null ? fieldMap.get(field).getDisplayName() : null;
            FieldChangeDTO fieldChangeDTO =
                    getFieldChangeDTO(getFullFieldName(table, field), displayName, old, newer);
            if (fieldChangeDTO != null) {
                fieldChangeDTOList.add(fieldChangeDTO);
            }
        }
        if (CollectionUtil.isEmpty(fieldChangeDTOList)) {
            return null;
        }
        return RowChangeDTO.builder()
                .columns(fieldChangeDTOList)
                .action(isDelete ? "d" : "u")
                .name(getUniqueName(recordMap, uniqueFields))
                .build();
    }

    public static RowChangeDTO convertInsertRecordToRowChangeDTO(
            Map<String, Object> recordMap,
            String table,
            Map<String, ModuleFieldDTO> fieldMap,
            List<String> uniqueFields) {

        if (CollectionUtil.isEmpty(recordMap)) {
            return null;
        }
        List<FieldChangeDTO> fieldChangeDTOList = new ArrayList<>();
        for (String field : recordMap.keySet()) {
            Object newer = recordMap.get(field);
            String displayName =
                    fieldMap.get(field) != null ? fieldMap.get(field).getDisplayName() : null;
            FieldChangeDTO fieldChangeDTO =
                    getFieldChangeDTO(getFullFieldName(table, field), displayName, null, newer);
            if (fieldChangeDTO != null) {
                fieldChangeDTOList.add(fieldChangeDTO);
            }
        }
        if (CollectionUtil.isEmpty(fieldChangeDTOList)) {
            return null;
        }
        return RowChangeDTO.builder()
                .columns(fieldChangeDTOList)
                .action("i")
                .name(getUniqueName(recordMap, uniqueFields))
                .build();
    }

    public static String getUniqueName(Map<String, Object> recordMap, List<String> uniqueFields) {

        StringBuilder stringBuilder = new StringBuilder();
        for (String uniqueField : uniqueFields) {
            if (recordMap.containsKey(uniqueField)) {
                Object value = getFieldWriteValue(recordMap.get(uniqueField));
                if (value != null) {
                    if (stringBuilder.length() > 0) {
                        stringBuilder.append(" ").append(String.valueOf(value));
                    }
                }
            }
        }
        return stringBuilder.toString();
    }

    public static FieldChangeDTO getFieldChangeDTO(
            String field, String displayName, Object old, Object newer) {

        try {
            String oldValue = String.valueOf(getFieldWriteValue(old));

            String newValue = String.valueOf(getFieldWriteValue(newer));

            if (!oldValue.equals(newValue)) {
                if (oldValue.equals("null")) {
                    return FieldChangeDTO.builder()
                            .field(field)
                            .old(null)
                            .newer(newValue)
                            .name(displayName)
                            .build();
                } else if (newValue.equals("null")) {
                    return FieldChangeDTO.builder()
                            .field(field)
                            .old(oldValue)
                            .newer(null)
                            .name(displayName)
                            .build();
                } else {
                    return FieldChangeDTO.builder()
                            .field(field)
                            .old(oldValue)
                            .newer(newValue)
                            .name(displayName)
                            .build();
                }
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static Map<String, ModuleFieldDTO> getModuleTableFieldMap(
            List<ModuleFieldDTO> moduleFieldDTOList, String tableName) {
        if (CollectionUtil.isEmpty(moduleFieldDTOList)) {
            return new HashMap<>();
        }
        return moduleFieldDTOList.stream()
                .filter(x -> StringUtils.equals(x.getTableName(), tableName))
                .collect(Collectors.toMap(x -> x.getColumnName(), x -> x));
    }

    public static List<String> getModuleTableUniqueNameField(
            List<ModuleFieldDTO> moduleFieldDTOList, String tableName) {
        if (CollectionUtil.isEmpty(moduleFieldDTOList)) {
            return new ArrayList<>();
        }
        return moduleFieldDTOList.stream()
                .filter(x -> x.getBizKeyOrder() != null)
                .sorted(Comparator.comparing(ModuleFieldDTO::getBizKeyOrder))
                .map(x -> x.getColumnName())
                .collect(Collectors.toList());
    }

    public static String getFullFieldName(String tableName, String field) {
        return tableName + "." + field;
    }

    public static Object getFieldWriteValue(Object value) {
        if (value == null) {
            return value;
        }
        try {
            if (value instanceof List list) {
                value = JsonUtil.obj2Json(list);
            } else if (value instanceof Map map) {
                value = JsonUtil.obj2Json(map);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return value;
    }
}
