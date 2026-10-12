package com.jdec.platform.data.biz.service;

import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.request.ForkVersionReq;
import com.jdec.platform.data.api.dto.request.TableDetailQueryReq;
import com.jdec.platform.data.api.dto.request.TableOptionReq;
import com.jdec.platform.data.api.dto.request.TablePageQueryReq;
import com.jdec.platform.data.api.dto.request.TableQueryReq;
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

    @Override
    public DataPage<DataBlock> queryByTable(TablePageQueryReq req) {
        return dataQueryService.queryByTable(req);
    }

    @Override
    public Long countByTable(TableQueryReq req) {
        return dataQueryService.countByTable(req);
    }

    @Override
    public DataBlock getDetailByTable(TableDetailQueryReq req) {
        return dataQueryService.getDetailByTable(req);
    }

    @Override
    public List<DataOptionResp> getOptionsByTable(TableOptionReq req) {
        return dataQueryService.getOptionsByTable(req);
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
