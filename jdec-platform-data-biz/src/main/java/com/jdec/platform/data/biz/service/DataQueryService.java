package com.jdec.platform.data.biz.service;

import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.request.DetailQueryReq;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.PageQueryReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.biz.plan.PlanMeta;
import com.jdec.platform.data.biz.plan.PlanMetaLoader;
import com.jdec.platform.data.biz.plan.compiler.QueryPlanCompiler;
import com.jdec.platform.data.biz.plan.executor.QueryPlanExecutor;
import com.jdec.platform.data.biz.plan.model.QueryPlan;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 动态查询服务门面 (DataQueryService)
 *
 * <p>彻底基于 Plan 编译与执行模型驱动：
 *
 * <ul>
 *   <li>1. 由 PlanMetaLoader 取一份元数据快照（模块树 + 表关系）；
 *   <li>2. 由 QueryPlanCompiler 以该快照将查询请求编译为 QueryPlan；
 *   <li>3. 由 QueryPlanExecutor 执行物理扫描、批量抓取与行内树装配；
 *   <li>4. 返回与 DataBlock / DataPage 同构的业务数据对象。
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataQueryService {

    private final QueryPlanCompiler queryPlanCompiler;
    private final QueryPlanExecutor queryPlanExecutor;
    private final PlanMetaLoader planMetaLoader;

    /** 标准动态分页查询门面 (PageQueryReq -> DataPage<DataBlock>) */
    public DataPage<DataBlock> queryPage(PageQueryReq req) {
        return query(req);
    }

    /** 标准动态列表全量查询门面 (QueryReq -> DataBlock，不分页，跳过 COUNT 统计) */
    public DataBlock queryList(QueryReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req, planMetaLoader.load(req.getModuleId()));
        return queryPlanExecutor.executeList(plan);
    }

    /** 标准通用查询门面 (QueryReq -> DataPage<DataBlock>) */
    public DataPage<DataBlock> query(QueryReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req, planMetaLoader.load(req.getModuleId()));
        return queryPlanExecutor.execute(plan);
    }

    /** 标准动态数据集数量统计 (QueryReq -> Long) */
    public Long count(QueryReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req, planMetaLoader.load(req.getModuleId()));
        return queryPlanExecutor.executeCount(plan);
    }

    /** 标准单据详情精准查询门面 (DetailQueryReq -> DataBlock 读写同源区块) */
    public DataBlock getDetail(DetailQueryReq req) {
        return getDetail(req, planMetaLoader.load(req.getModuleId()));
    }

    /** 标准单据详情精准查询门面（复用调用方已加载的元数据快照） */
    public DataBlock getDetail(DetailQueryReq req, PlanMeta meta) {
        QueryPlan plan = queryPlanCompiler.compileDetail(req, meta);
        DataPage<DataBlock> page = queryPlanExecutor.execute(plan);
        return page != null && page.getData() != null
                ? page.getData()
                : DataBlock.empty(req.getModuleId());
    }

    /** 标准字段搜索下拉候选项查询 (OptionReq -> List<DataOptionResp>) */
    public List<DataOptionResp> getOptions(OptionReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req, planMetaLoader.load(req.getModuleId()));
        List<Object> values =
                queryPlanExecutor.executeDistinctOptions(
                        plan,
                        req.getTableName(),
                        req.getColumnName(),
                        req.getDirection(),
                        req.getLimit());

        return values.stream()
                .filter(Objects::nonNull)
                .map(val -> DataOptionResp.builder().label(val).value(val).build())
                .toList();
    }
}
