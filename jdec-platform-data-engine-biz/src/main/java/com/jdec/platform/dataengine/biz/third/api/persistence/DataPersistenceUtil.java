package com.jdec.platform.dataengine.biz.third.api.persistence;

import com.jdec.platform.dataengine.biz.constants.CommonConstant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jooq.JSON;
import org.jooq.Record;

public class DataPersistenceUtil {

    public static Long getRecordId(Map<String, Object> record) {
        Object idObj = record.get(CommonConstant.TABLE_DEFAULT_PRIMARY_KEY_FIELD);
        Long id = idObj instanceof Number num ? num.longValue() : null;
        return id;
    }

    public static Map<String, Object> getTableRecordMap(
            Map<String, Object> recordMap, String tableName) {
        Map<String, Object> tableRecord = new HashMap<>();
        recordMap.forEach(
                (key, value) -> {
                    if (key.startsWith(tableName)) {
                        tableRecord.put(key.substring(tableName.length() + 1), value);
                    }
                });
        return tableRecord;
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

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> dataMap : dataMapList) {
            Map<String, Object> tableRecord = (Map<String, Object>) dataMap.get("data");
            result.add(tableRecord);
        }
        return result;
    }

    public static Map<String, Object> convertRecordToMap(Record r) {
        Map<String, Object> map = new HashMap<>();
        if (r != null && r.fields() != null) {
            for (org.jooq.Field field : r.fields()) {
                Object val = r.get(field);
                if (val instanceof JSON v) {
                    map.put(field.getName(), v.data());
                } else {
                    map.put(field.getName(), val);
                }
            }
        }
        return map;
    }
}
