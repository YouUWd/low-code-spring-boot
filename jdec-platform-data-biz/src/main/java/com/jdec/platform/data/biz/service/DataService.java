package com.jdec.platform.data.biz.service;

import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.request.SaveReq;
import com.jdec.platform.data.api.dto.response.DataDetail;
import com.jdec.platform.data.api.dto.response.DataOption;
import com.jdec.platform.data.api.dto.response.DataPage;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 数据引擎核心实现类 (统一承载单模块与多模块的读写与元数据透传内核) */
@Service
@RequiredArgsConstructor
public class DataService implements DataApi {

    private final DataQueryService dataQueryService;
    private final DataPersistenceService dataPersistenceService;

    @Override
    public DataPage<Map<String, Object>> query(QueryReq req) {
        return dataQueryService.query(req);
    }

    @Override
    public List<DataOption> getOptions(OptionReq req) {
        return dataQueryService.getOptions(req);
    }

    @Override
    public DataDetail<Map<String, Object>> getDetail(Long moduleId, Long id, List<Long> fields) {
        return dataQueryService.getDetail(moduleId, id, fields);
    }

    @Override
    public Long save(SaveReq req) {
        return dataPersistenceService.save(req);
    }
}
