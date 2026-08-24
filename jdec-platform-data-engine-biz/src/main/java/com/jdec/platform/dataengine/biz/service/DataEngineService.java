package com.jdec.platform.dataengine.biz.service;

import com.jdec.platform.dataengine.api.DataEngineApi;
import com.jdec.platform.dataengine.api.dto.request.DynamicQueryReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.api.dto.response.DynamicDetailResp;
import com.jdec.platform.dataengine.api.dto.response.DynamicQueryResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataEngineService implements DataEngineApi {

    private final DynamicQueryService dynamicQueryService;
    private final DynamicPersistenceService dynamicPersistenceService;

    @Override
    public DynamicQueryResp query(DynamicQueryReq req) {
        return dynamicQueryService.query(req);
    }

    @Override
    public DynamicDetailResp getDetail(Long moduleId, Long id) {
        return dynamicQueryService.getDetail(moduleId, id);
    }

    @Override
    public Long save(DynamicSaveReq req) {
        return dynamicPersistenceService.save(req);
    }
}
