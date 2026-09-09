package com.jdec.platform.dataengine.biz.third.api.persistence;

import com.jdec.platform.dataengine.biz.constants.CommonConstant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
}
