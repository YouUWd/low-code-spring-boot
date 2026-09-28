package com.jdec.platform.dataengine.biz.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.dataengine.biz.entity.WordMappingDict;
import com.jdec.platform.dataengine.biz.mapper.WordMappingDictMapper;
import com.jdec.platform.dataengine.biz.service.WordMappingDictService;
import com.jdec.platform.shared.context.AppContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class WordMappingDictServiceImpl implements WordMappingDictService {

    private final WordMappingDictMapper wordMappingDictMapper;

    @Override
    public Boolean saveWordMappingDictMap(Map<Long, Map<String, Object>> wordMap) {
        if (CollectionUtil.isEmpty(wordMap)) {
            return false;
        }
        List<Long> wordIdList = wordMap.keySet().stream().toList();
        List<WordMappingDict> mappingDictList =
                wordMappingDictMapper.selectList(
                        new LambdaQueryWrapper<WordMappingDict>()
                                .in(WordMappingDict::getWordId, wordIdList));
        Map<Long, WordMappingDict> wordMappingDictMap = new HashMap();
        if (CollectionUtil.isNotEmpty(mappingDictList)) {
            wordMappingDictMap =
                    mappingDictList.stream().collect(Collectors.toMap(x -> x.getWordId(), x -> x));
        }
        List<WordMappingDict> insertList = new ArrayList<>();
        List<WordMappingDict> updateList = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Object>> entry : wordMap.entrySet()) {
            if (CollectionUtil.isEmpty(entry.getValue())) {
                continue;
            }
            Long wordId = entry.getKey();
            // 是否已经存在
            WordMappingDict mappingDict = wordMappingDictMap.get(wordId);
            if (mappingDict == null) {
                WordMappingDict dict = new WordMappingDict();
                dict.setWordId(entry.getKey());
                setWordMappingDictByMap(dict, entry.getValue());
                dict.setCreateDate(LocalDateTime.now());
                dict.setUpdateDate(LocalDateTime.now());
                dict.setCreateBy(AppContext.getUserId());
                dict.setUpdateBy(AppContext.getUserId());
                insertList.add(dict);
            } else {
                setWordMappingDictByMap(mappingDict, entry.getValue());
                mappingDict.setUpdateDate(LocalDateTime.now());
                mappingDict.setUpdateBy(AppContext.getUserId());
                updateList.add(mappingDict);
            }
        }
        if (CollectionUtil.isNotEmpty(insertList)) {
            wordMappingDictMapper.insert(insertList);
        }
        if (CollectionUtil.isNotEmpty(updateList)) {
            wordMappingDictMapper.updateById(insertList);
        }
        return true;
    }

    private void setWordMappingDictByMap(WordMappingDict dict, Map<String, Object> dataMap) {
        dict.setBusinessNo((String) dataMap.get("business_no"));
        dict.setWordNo((String) dataMap.get("word_no"));
        dict.setAliasValueTitle((String) dataMap.get("alias_value_title"));
        dict.setBgColor((String) dataMap.get("bg_color"));
        dict.setColor((String) dataMap.get("color"));
        dict.setBorderColor((String) dataMap.get("border_color"));
        dict.setNodeIcon((String) dataMap.get("node_icon"));
        dict.setPath((String) dataMap.get("path"));
    }

    @Override
    public Map<Long, WordMappingDict> getWordMappingDictMap(List<Long> wordIdList) {
        List<WordMappingDict> dictList = wordMappingDictMapper.selectByIds(wordIdList);
        if (CollectionUtil.isEmpty(dictList)) {
            return Map.of();
        }
        return dictList.stream().collect(Collectors.toMap(x -> x.getWordId(), x -> x));
    }
}
