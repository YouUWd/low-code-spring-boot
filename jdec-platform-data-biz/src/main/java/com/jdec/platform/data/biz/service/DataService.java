package com.jdec.platform.data.biz.service;

import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.request.DetailQueryReq;
import com.jdec.platform.data.api.dto.request.ForkVersionReq;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.PageQueryReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.DataSaveResp;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 数据引擎核心实现类 (统一承载单模块与多模块的读写与元数据透传内核) */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataService implements DataApi {

    private final DataQueryService dataQueryService;
    private final DataPersistenceService dataPersistenceService;
    private final ForkVersionService forkVersionService;
    private final SingleTableQueryService singleTableQueryService;

    @Override
    public DataPage<DataBlock> queryPage(PageQueryReq req) {
        return dataQueryService.queryPage(req);
    }

    @Override
    public DataBlock queryList(QueryReq req) {
        return dataQueryService.queryList(req);
    }

    @Override
    public Long count(QueryReq req) {
        return dataQueryService.count(req);
    }

    @Override
    public DataBlock getDetail(DetailQueryReq req) {
        return dataQueryService.getDetail(req);
    }

    @Override
    public List<DataOptionResp> getOptions(OptionReq req) {
        return dataQueryService.getOptions(req);
    }

    @Override
    public List<java.util.Map<String, Object>> querySingleTableList(
            com.jdec.platform.data.api.dto.request.SingleTableQueryReq req) {
        return singleTableQueryService.querySingleTableList(req);
    }

    @Override
    public <T> List<T> querySingleTableColumn(
            com.jdec.platform.data.api.dto.request.SingleTableQueryReq req, Class<T> targetType) {
        return singleTableQueryService.querySingleTableColumn(req, targetType);
    }

    @Override
    public Long countSingleTable(com.jdec.platform.data.api.dto.request.SingleTableQueryReq req) {
        return singleTableQueryService.countSingleTable(req);
    }

    @Override
    public List<Long> getLatestVersionIds(
            String tableName, java.util.Collection<String> businessNos, Integer businessStatus) {
        return singleTableQueryService.getLatestVersionIds(tableName, businessNos, businessStatus);
    }

    @Override
    public DataSaveResp save(DataBlock block) {
        return dataPersistenceService.save(block);
    }

    @Override
    public Long forkNewVersion(ForkVersionReq req) {
        return forkVersionService.forkNewVersion(req);
    }
}
