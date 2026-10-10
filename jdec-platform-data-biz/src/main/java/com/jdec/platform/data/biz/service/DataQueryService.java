package com.jdec.platform.data.biz.service;

import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.request.DetailQueryReq;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.PageQueryReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.response.DataOption;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.biz.plan.compiler.QueryPlanCompiler;
import com.jdec.platform.data.biz.plan.executor.QueryPlanExecutor;
import com.jdec.platform.data.biz.plan.model.QueryPlan;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 动态查询服务门面 (DataQueryService)
 *
 * <p>彻底基于 Plan 编译与执行模型驱动：
 *
 * <ul>
 *   <li>1. 由 QueryPlanCompiler 统一将查询请求编译为 QueryPlan；
 *   <li>2. 由 QueryPlanExecutor 执行物理扫描、批量抓取与行内树装配；
 *   <li>3. 返回与 DataBlock / DataPage 同构的业务数据对象。
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataQueryService {

    private final QueryPlanCompiler queryPlanCompiler;
    private final QueryPlanExecutor queryPlanExecutor;

    /** 核心通用动态模块树分页查询 */
    public DataPage<DataBlock> query(PageQueryReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req);
        return queryPlanExecutor.execute(plan);
    }

    /** 兼容基类查询 (未指定分页时使用默认分页) */
    public DataPage<DataBlock> query(QueryReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req);
        return queryPlanExecutor.execute(plan);
    }

    /** 通用动态模块树数量统计 (仅执行 Stage 0 根节点计数扫描) */
    public Long count(QueryReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req);
        return queryPlanExecutor.executeCount(plan);
    }

    /** 单据详情精准查询门面 (DetailQueryReq -> DataBlock 读写同源区块) */
    public DataBlock getDetail(DetailQueryReq req) {
        QueryPlan plan = queryPlanCompiler.compileDetail(req);
        DataPage<DataBlock> page = queryPlanExecutor.execute(plan);
        return page != null && page.getData() != null
                ? page.getData()
                : DataBlock.empty(req.getModuleId());
    }

    /** 物理表分页查询门面 (TablePageQueryReq -> DataPage<DataBlock>) */
    public DataPage<DataBlock> queryByTable(
            com.jdec.platform.data.api.dto.request.TablePageQueryReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req);
        return queryPlanExecutor.execute(plan);
    }

    /** 物理表通用查询门面 (TableQueryReq -> DataPage<DataBlock>) */
    public DataPage<DataBlock> queryByTable(
            com.jdec.platform.data.api.dto.request.TableQueryReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req);
        return queryPlanExecutor.execute(plan);
    }

    /** 物理表动态数据集数量统计 (TableQueryReq -> Long) */
    public Long countByTable(com.jdec.platform.data.api.dto.request.TableQueryReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req);
        return queryPlanExecutor.executeCount(plan);
    }

    /** 物理表详情精准查询门面 (TableDetailQueryReq -> DataBlock 读写同源区块) */
    public DataBlock getDetailByTable(
            com.jdec.platform.data.api.dto.request.TableDetailQueryReq req) {
        QueryPlan plan = queryPlanCompiler.compileDetail(req);
        DataPage<DataBlock> page = queryPlanExecutor.execute(plan);
        return page != null && page.getData() != null
                ? page.getData()
                : DataBlock.empty(req.getModuleId());
    }

    /** 动态单据详情精准查询快捷方法 (无下级展开) */
    public DataBlock getDetail(Long moduleId, Long id) {
        return getDetail(DetailQueryReq.builder().moduleId(moduleId).id(id).build());
    }

    /** 动态单据详情精准查询快捷方法 (带投影字段) */
    public DataBlock getDetail(Long moduleId, Long id, List<Long> fields) {
        return getDetail(DetailQueryReq.builder().moduleId(moduleId).id(id).fields(fields).build());
    }

    /** 物理表字段搜索下拉候选项查询 (TableOptionReq -> List<DataOption>) */
    public List<DataOption> getOptionsByTable(
            com.jdec.platform.data.api.dto.request.TableOptionReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req);
        List<Object> values =
                queryPlanExecutor.executeDistinctOptions(
                        plan,
                        req.getTableName(),
                        req.getColumnName(),
                        req.getDirection(),
                        req.getLimit());

        List<DataOption> options = new ArrayList<>();
        for (Object val : values) {
            if (val != null) {
                options.add(DataOption.builder().label(val).value(val).build());
            }
        }
        return options;
    }

    /** 通用字段搜索下拉候选项查询 (基于 QueryPlan 统一编译与执行，旧版将废弃) */
    @Deprecated(since = "1.0.0", forRemoval = true)
    public List<DataOption> getOptions(OptionReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req);
        List<Object> values =
                queryPlanExecutor.executeDistinctOptions(
                        plan, req.getFieldId(), req.getDirection());

        List<DataOption> options = new ArrayList<>();
        for (Object val : values) {
            if (val != null) {
                options.add(DataOption.builder().label(val).value(val).build());
            }
        }
        return options;
    }
}
