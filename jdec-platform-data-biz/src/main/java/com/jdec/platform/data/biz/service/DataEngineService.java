package com.jdec.platform.data.biz.service;

import com.jdec.platform.data.api.DataEngineApi;
import com.jdec.platform.data.api.dto.request.BatchDynamicQueryReq;
import com.jdec.platform.data.api.dto.request.BatchDynamicSaveReq;
import com.jdec.platform.data.api.dto.request.DynamicDetailReq;
import com.jdec.platform.data.api.dto.request.DynamicQueryReq;
import com.jdec.platform.data.api.dto.request.DynamicSaveReq;
import com.jdec.platform.data.api.dto.response.BatchEngineDataResult;
import com.jdec.platform.data.api.dto.response.BatchSaveResp;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.EngineDataResult;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 数据引擎核心实现类 (统一承载单模块与多模块的读写与元数据透传内核) */
@Service
@RequiredArgsConstructor
public class DataEngineService implements DataEngineApi {

    private final DynamicQueryService dynamicQueryService;
    private final DynamicPersistenceService dynamicPersistenceService;

    @Override
    public EngineDataResult<DataPage<Map<String, Object>>> query(DynamicQueryReq req) {
        return dynamicQueryService.query(req);
    }

    @Override
    public BatchEngineDataResult batchQuery(BatchDynamicQueryReq req) {
        return dynamicQueryService.batchQuery(req);
    }

    @Override
    public EngineDataResult<Map<String, Object>> getDetail(DynamicDetailReq req) {
        return dynamicQueryService.getDetail(req);
    }

    @Override
    public Long save(DynamicSaveReq req) {
        return dynamicPersistenceService.save(req);
    }

    @Override
    public BatchSaveResp batchSave(BatchDynamicSaveReq req) {
        return dynamicPersistenceService.batchSave(req);
    }
}
