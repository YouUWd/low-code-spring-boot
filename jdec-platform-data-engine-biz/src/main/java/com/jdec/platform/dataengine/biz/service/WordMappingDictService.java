package com.jdec.platform.dataengine.biz.service;

import com.jdec.platform.dataengine.biz.entity.WordMappingDict;
import java.util.List;
import java.util.Map;

public interface WordMappingDictService {

    public Boolean saveWordMappingDictMap(Map<Long, Map<String, Object>> wordMap);

    public Map<Long, WordMappingDict> getWordMappingDictMap(List<Long> wordIdList);
}
