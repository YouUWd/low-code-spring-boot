package com.jdec.platform.data.biz.service;

import com.jdec.platform.data.api.DataApi;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.request.DetailQueryReq;
import com.jdec.platform.data.api.dto.request.ForkVersionReq;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.PageQueryReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.request.TableDetailQueryReq;
import com.jdec.platform.data.api.dto.request.TableOptionReq;
import com.jdec.platform.data.api.dto.request.TablePageQueryReq;
import com.jdec.platform.data.api.dto.request.TableQueryReq;
import com.jdec.platform.data.api.dto.response.DataOption;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.SaveResp;
import com.jdec.platform.data.biz.util.TreeMergeUtils;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 数据引擎核心实现类 (统一承载单模块与多模块的读写与元数据透传内核) */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataService implements DataApi {

    private final DataQueryService dataQueryService;
    private final DataPersistenceService dataPersistenceService;
    private final JooqDataContextFactory jooqDataContextFactory;

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
    public List<DataOption> getOptionsByTable(TableOptionReq req) {
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long forkNewVersion(ForkVersionReq req) {
        if (req == null || req.getModuleId() == null || req.getBaseId() == null) {
            throw new IllegalArgumentException(
                    "moduleId and baseId cannot be null for forkNewVersion");
        }

        // 1. 抓取基准单据完整物理快照 (含伴生表与所有子表全部列)
        DataBlock baseBlock = getFullDetail(req.getModuleId(), req.getBaseId());
        if (baseBlock == null || baseBlock.isEmpty()) {
            throw new IllegalArgumentException("Base record not found for id: " + req.getBaseId());
        }

        // 2. 深度合并局部增量数据
        DataBlock merged = TreeMergeUtils.deepMerge(baseBlock, req.getPatchData());

        // 3. 递归清洗树主键 ID 与关联外键，重置审计字段
        TreeMergeUtils.stripIdsAndResetAudit(merged);

        // 4. 从数据快照自解析主表名与 business_no，就地查询 MAX(version_no) + 1 并在整树下发
        String businessNo = TreeMergeUtils.extractBusinessNo(baseBlock);
        String primaryTable = TreeMergeUtils.extractPrimaryTable(baseBlock);
        if (businessNo != null
                && !businessNo.isBlank()
                && primaryTable != null
                && !primaryTable.isBlank()) {
            Integer nextVersion = computeNextVersion(primaryTable, businessNo);
            TreeMergeUtils.applyVersionNo(merged, nextVersion);
            log.info(
                    "版本派生/克隆: 主表={}, 单据号={}, 原始基准ID={}, 计算生成最新版本号={}",
                    primaryTable,
                    businessNo,
                    req.getBaseId(),
                    nextVersion);
        }

        // 5. 调用底层保存引擎落库生成新版本
        SaveResp saveResp = save(merged);
        return saveResp != null ? saveResp.getRootId() : null;
    }

    private Integer computeNextVersion(String primaryTable, String businessNo) {
        DSLContext dsl = jooqDataContextFactory.getContext();
        Integer maxVersion = null;
        try {
            maxVersion =
                    dsl.select(
                                    DSL.max(
                                            DSL.field(
                                                    DSL.name(primaryTable, "version_no"),
                                                    Integer.class)))
                            .from(DSL.table(DSL.name(primaryTable)))
                            .where(DSL.field(DSL.name(primaryTable, "business_no")).eq(businessNo))
                            .fetchOne(0, Integer.class);
        } catch (Exception ex) {
            log.warn("查询主表 [{}] 单据 [{}] 最大版本号失败: {}", primaryTable, businessNo, ex.getMessage());
        }

        return (maxVersion != null ? maxVersion : 0) + 1;
    }
}
