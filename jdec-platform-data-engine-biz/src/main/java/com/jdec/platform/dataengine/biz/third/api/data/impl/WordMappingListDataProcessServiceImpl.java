package com.jdec.platform.dataengine.biz.third.api.data.impl;

import com.jdec.platform.dataengine.api.dto.enums.QueryType;
import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import com.jdec.platform.dataengine.biz.service.WordMappingDictService;
import com.jdec.platform.dataengine.biz.third.api.data.ListDataProcessService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 数据行上 词云字典值的处理 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WordMappingListDataProcessServiceImpl implements ListDataProcessService {

    private final WordMappingDictService wordMappingDictService;

    @Override
    public void processAssembleData(
            List<Map<String, Object>> dataList,
            Map<Object, Object> returnMap,
            DataIntegrationContext context) {

        /**
         * // 过滤出所有的词云 字段 Set<String> filedSet =
         * DataProcessUtils.filterWordMapField(context.getModuleFilterInfo());
         *
         * <p>// 获取所有的词云字段的ID 值 List<Long> wordIdList =
         * DataProcessUtils.filterWordIdByList(dataList, filedSet);
         *
         * <p>// 查询词云字典值 Map<Long, WordMappingDict> wordMappingDictMap =
         * wordMappingDictService.getWordMappingDictMap(wordIdList);
         *
         * <p>// 构建词云字典值 DataProcessUtils.fillListMapUnitiveData( dataList,
         * ResultSetConstantKey.RESULT_SET_KEY_WORD_MAPPING, wordMappingDictMap);
         */
    }

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return true;
    }
}
