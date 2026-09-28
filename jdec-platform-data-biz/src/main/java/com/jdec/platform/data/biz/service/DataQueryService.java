package com.jdec.platform.data.biz.service;

import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.response.DataDetail;
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
 * 动态查询服务门面 (DynamicQueryService)
 *
 * <p>彻底基于 Plan 编译与执行模型驱动：
 *
 * <ul>
 *   <li>1. 由 QueryPlanCompiler 统一将自相似请求树编译为 QueryPlan；
 *   <li>2. 由 QueryPlanExecutor 执行物理扫描、批量抓取与行内树装配；
 *   <li>3. 保持与 DataEngineApi, DynamicDetailReq, DynamicOptionReq 的门面兼容。
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataQueryService {

    private final QueryPlanCompiler queryPlanCompiler;
    private final QueryPlanExecutor queryPlanExecutor;

    /** 核心通用动态模块树查询 */
    public DataPage<Map<String, Object>> query(QueryReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req);
        return queryPlanExecutor.execute(plan);
    }

    /** 通用动态模块树数量统计 (仅执行 Stage 0 根节点计数扫描) */
    public Long count(QueryReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req);
        return queryPlanExecutor.executeCount(plan);
    }

    /** 动态单据详情精准查询快捷方法 (无下级展开) */
    public DataDetail<Map<String, Object>> getDetail(Long moduleId, Long id) {
        return getDetail(moduleId, id, (List<Long>) null);
    }

    /** 动态单据详情精准查询门面 (底层以 compileDetail 驱动，返回与 DataPage 同构的 DataDetail 实体记录) */
    public DataDetail<Map<String, Object>> getDetail(Long moduleId, Long id, List<Long> fields) {
        QueryPlan plan = queryPlanCompiler.compileDetail(moduleId, id, fields);
        DataPage<Map<String, Object>> page = queryPlanExecutor.execute(plan);
        return DataDetail.of(moduleId, page != null ? page.getRecords() : Collections.emptyList());
    }

    /** 通用字段搜索下拉候选项查询 (基于 QueryPlan 统一编译与执行) */
    public List<DataOption> getOptions(OptionReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req);
        List<String> values =
                queryPlanExecutor.executeDistinctOptions(
                        plan, req.getFieldId(), req.getDirection());

        List<DataOption> options = new ArrayList<>();
        for (String val : values) {
            if (val != null) {
                options.add(DataOption.builder().label(val).value(val).build());
            }
        }
        return options;
    }
}
