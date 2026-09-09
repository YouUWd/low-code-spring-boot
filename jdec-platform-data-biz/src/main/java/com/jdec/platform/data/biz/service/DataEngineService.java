package com.jdec.platform.data.biz.service;

import com.jdec.platform.data.api.DataEngineApi;
import com.jdec.platform.data.api.dto.request.DynamicOptionReq;
import com.jdec.platform.data.api.dto.request.DynamicQueryReq;
import com.jdec.platform.data.api.dto.request.DynamicSaveReq;
import com.jdec.platform.data.api.dto.request.EngineHeaderReq;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.DynamicOptionItem;
import com.jdec.platform.data.api.dto.response.EngineDataResult;
import com.jdec.platform.data.api.dto.response.EngineHeaderResp;
import java.util.List;
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
    public EngineHeaderResp getHeader(EngineHeaderReq req) {
        return dynamicQueryService.getHeader(req);
    }

    @Override
    public DataPage<Map<String, Object>> query(DynamicQueryReq req) {
        return dynamicQueryService.query(req);
    }

    @Override
    public List<DynamicOptionItem> getOptions(DynamicOptionReq req) {
        return dynamicQueryService.getOptions(req);
    }

    @Override
    public EngineDataResult<Map<String, Object>> getDetail(
            Long moduleId, Long id, List<DynamicQueryReq> children) {
        return dynamicQueryService.getDetail(moduleId, id, children);
    }

    @Override
    public Long save(DynamicSaveReq req) {
        return dynamicPersistenceService.save(req);
    }
}
