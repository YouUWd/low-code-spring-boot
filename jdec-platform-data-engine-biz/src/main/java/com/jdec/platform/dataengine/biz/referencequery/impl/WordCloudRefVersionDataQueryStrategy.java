package com.jdec.platform.dataengine.biz.referencequery.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jdec.platform.dataengine.biz.context.VersionDataQueryContext;
import com.jdec.platform.dataengine.biz.enums.ReferenceBizType;
import com.jdec.platform.dataengine.biz.referencequery.VersionDataQueryStrategy;
import com.jdec.platform.shared.third.WordApiClient;
import java.util.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

// 词云数据查询实现类
@Service
@AllArgsConstructor
public class WordCloudRefVersionDataQueryStrategy implements VersionDataQueryStrategy {

    private final WordApiClient wordApiClient;

    @Override
    public List<Map<String, Object>> queryVersionDatas(
            VersionDataQueryContext versionDataQueryContext) {
        List<RelationWordParam> params = new ArrayList<>();

        versionDataQueryContext
                .getRelationNoWordUUIDMap()
                .entrySet()
                .forEach(
                        entry -> {
                            params.add(new RelationWordParam(entry.getKey(), entry.getValue()));
                        });

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("param", params);

        List<Map<String, Object>> wordResult =
                wordApiClient.getRelationWordEdit(paramMap, new TypeReference<>() {});

        return wordResult;
    }

    @Override
    public Boolean isRefTo(String bizType) {
        return ReferenceBizType.WORD_CLOUD.getType().equals(bizType);
    }

    public record RelationWordParam(String business_no, Set<String> word_uuid) {}
}
