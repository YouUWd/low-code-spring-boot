package com.jdec.platform.data.biz.service;

import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.request.DetailQueryReq;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.PageQueryReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.response.DataOption;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.SaveResp;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 数据引擎核心实现类 (统一承载单模块与多模块的读写与元数据透传内核) */
@Service
@RequiredArgsConstructor
public class DataService implements DataApi {

    private final DataQueryService dataQueryService;
    private final DataPersistenceService dataPersistenceService;

    @Override
    public DataPage<DataBlock> query(PageQueryReq req) {
        return dataQueryService.query(req);
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
    public DataPage<DataBlock> queryByTable(
            com.jdec.platform.data.api.dto.request.TablePageQueryReq req) {
        return dataQueryService.queryByTable(req);
    }

    @Override
    public Long countByTable(com.jdec.platform.data.api.dto.request.TableQueryReq req) {
        return dataQueryService.countByTable(req);
    }

    @Override
    public DataBlock getDetailByTable(
            com.jdec.platform.data.api.dto.request.TableDetailQueryReq req) {
        return dataQueryService.getDetailByTable(req);
    }

    @Override
    public List<DataOption> getOptionsByTable(
            com.jdec.platform.data.api.dto.request.TableOptionReq req) {
        return dataQueryService.getOptionsByTable(req);
    }

    @Override
    public List<DataOption> getOptions(OptionReq req) {
        return dataQueryService.getOptions(req);
    }

    @Override
    public SaveResp save(DataBlock block) {
        return dataPersistenceService.save(block);
    }
}
