package com.jdec.platform.dataengine.biz.third.api.data.impl;

import com.jdec.platform.dataengine.api.dto.enums.QueryType;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import com.jdec.platform.dataengine.biz.service.WordMappingDictService;
import com.jdec.platform.dataengine.biz.third.api.data.DetailDataProcessService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 详情数据 词云字典处理 */
@Service
@RequiredArgsConstructor
public class WordMappingDetailDataProcessServiceImpl implements DetailDataProcessService {

    private final WordMappingDictService wordMappingDictService;

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return true;
    }

    @Override
    public void processAssembleData(
            List<NestedModuleDataDTO> records, DataIntegrationContext context) {

        /**
         * // 过滤出所有的词云 字段 Set<String> filedSet =
         * DataProcessUtils.filterWordMapField(context.getModuleFilterInfo());
         *
         * <p>// 获取所有的词云字段的ID 值 List<Long> wordIdList =
         * DataProcessUtils.filterWordIdByMap(moduleData, filedSet);
         *
         * <p>// 查询词云字典值 Map<Long, WordMappingDict> wordMappingDictMap =
         * wordMappingDictService.getWordMappingDictMap(wordIdList);
         *
         * <p>// 构建词云字典值 DataProcessUtils.fillMapUnitiveData( moduleData,
         * ResultSetConstantKey.RESULT_SET_KEY_WORD_MAPPING, wordMappingDictMap);
         */
    }
}
