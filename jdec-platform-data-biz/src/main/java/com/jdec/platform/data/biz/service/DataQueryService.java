package com.jdec.platform.data.biz.service;

import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.request.TableDetailQueryReq;
import com.jdec.platform.data.api.dto.request.TableOptionReq;
import com.jdec.platform.data.api.dto.request.TablePageQueryReq;
import com.jdec.platform.data.api.dto.request.TableQueryReq;
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
 *   <li>2. 由 QueryPlanCompiler 以该快照将物理表/列查询请求编译为 QueryPlan；
 *   <li>3. 由 QueryPlanExecutor 执行物理扫描、批量抓取与行内树装配；
 *   <li>4. 返回与 DataBlock / DataPage 同构的业务数据对象。
 * </ul>
 *
 * <p>无快照参数的重载会在编译前即时加载快照；带 {@link PlanMeta} 参数的重载则复用调用方已加载的同一份
 * 快照，供「一次操作内多次编译」的场景（如版本派生先读后写）避免元数据漂移。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataQueryService {

    private final QueryPlanCompiler queryPlanCompiler;
    private final QueryPlanExecutor queryPlanExecutor;
    private final PlanMetaLoader planMetaLoader;

    /** 物理表分页查询门面 (TablePageQueryReq -> DataPage<DataBlock>) */
    public DataPage<DataBlock> queryByTable(TablePageQueryReq req) {
        // TablePageQueryReq 是 TableQueryReq 的子类，编译内核按运行时类型读取分页参数，直接委托即可
        return queryByTable((TableQueryReq) req);
    }

    /** 物理表通用查询门面 (TableQueryReq -> DataPage<DataBlock>) */
    public DataPage<DataBlock> queryByTable(TableQueryReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req, planMetaLoader.load(req.getModuleId()));
        return queryPlanExecutor.execute(plan);
    }

    /** 物理表动态数据集数量统计 (TableQueryReq -> Long) */
    public Long countByTable(TableQueryReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req, planMetaLoader.load(req.getModuleId()));
        return queryPlanExecutor.executeCount(plan);
    }

    /** 物理表详情精准查询门面 (TableDetailQueryReq -> DataBlock 读写同源区块) */
    public DataBlock getDetailByTable(TableDetailQueryReq req) {
        return getDetailByTable(req, planMetaLoader.load(req.getModuleId()));
    }

    /** 物理表详情精准查询门面（复用调用方已加载的元数据快照） */
    public DataBlock getDetailByTable(TableDetailQueryReq req, PlanMeta meta) {
        QueryPlan plan = queryPlanCompiler.compileDetail(req, meta);
        DataPage<DataBlock> page = queryPlanExecutor.execute(plan);
        return page != null && page.getData() != null
                ? page.getData()
                : DataBlock.empty(req.getModuleId());
    }

    /** 物理表字段搜索下拉候选项查询 (TableOptionReq -> List<DataOptionResp>) */
    public List<DataOptionResp> getOptionsByTable(TableOptionReq req) {
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
