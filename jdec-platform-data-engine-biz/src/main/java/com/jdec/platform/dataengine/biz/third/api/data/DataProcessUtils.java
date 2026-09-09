package com.jdec.platform.dataengine.biz.third.api.data;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.biz.dto.ModuleField;
import com.jdec.platform.dataengine.biz.dto.ModuleFilterInfo;
import java.util.*;
import org.apache.commons.lang3.StringUtils;

public class DataProcessUtils {

    public static Set<String> filterWordMapField(ModuleFilterInfo moduleFilterInfo) {
        Set<String> filedSet = new HashSet<>();
        for (ModuleField mf : moduleFilterInfo.getAllFields()) {
            if (StringUtils.isEmpty(mf.getRelationBusinessNo())) {
                continue;
            }
            filedSet.add(mf.getFieldCode());
        }
        return filedSet;
    }

    public static List<Long> filterWordIdByList(
            List<Map<String, Object>> dataList, Set<String> filedSet) {
        List<Long> wordIdList = new ArrayList<>();
        for (Map data : dataList) {
            List<Long> wordIds = filterWordIdByMap(data, filedSet);
            if (CollectionUtil.isNotEmpty(wordIds)) {
                wordIdList.addAll(wordIds);
            }
        }
        return wordIdList;
    }

    public static List<Long> filterWordIdByMap(Map data, Set<String> filedSet) {
        List<Long> wordIdList = new ArrayList<>();
        for (String filed : filedSet) {
            Object fieldValue = data.get(filed);
            if (fieldValue == null) {
                continue;
            }
            wordIdList.add(Long.valueOf(fieldValue.toString()));
        }
        return wordIdList;
    }

    public static void fillListMapUnitiveData(
            List<Map<String, Object>> dataList, String key, Object data) {
        if (CollectionUtil.isEmpty(dataList) || data == null) {
            return;
        }
        for (Map dataMap : dataList) {
            fillMapUnitiveData(dataMap, key, data);
        }
    }

    public static void fillMapUnitiveData(Map dataMap, String key, Object data) {
        if (dataMap == null || data == null) {
            return;
        }
        dataMap.put(key, data);
    }
}
