package com.jdec.platform.data.biz.service;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.data.api.dto.response.DataDetail;
import com.jdec.platform.data.api.dto.response.DataOption;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.biz.plan.compiler.QueryPlanCompiler;
import com.jdec.platform.data.biz.plan.executor.QueryPlanExecutor;
import com.jdec.platform.data.biz.plan.model.QueryPlan;
import com.jdec.platform.shared.context.AppContext;
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

    private final ConfigApi configApi;
    private final QueryPlanCompiler queryPlanCompiler;
    private final QueryPlanExecutor queryPlanExecutor;

    /** 核心通用动态模块树查询 */
    public DataPage<Map<String, Object>> query(QueryReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req);
        return queryPlanExecutor.execute(plan);
    }

    /** 动态单据详情精准查询快捷方法 (无下级展开) */
    public DataDetail<Map<String, Object>> getDetail(Long moduleId, Long id) {
        return getDetail(moduleId, id, (List<Long>) null);
    }

    /** 动态单据详情精准查询门面 (底层以 query 驱动，第一层级保证且只返回单条记录 Map，下级子模块自相似嵌套) */
    public DataDetail<Map<String, Object>> getDetail(Long moduleId, Long id, List<Long> fields) {
        if (moduleId == null || id == null) {
            throw new IllegalArgumentException("模块 ID 和单据 ID 不能为空");
        }

        ModuleInfo meta =
                configApi.getModuleWithFields(
                        AppContext.getProjectNo(), AppContext.getSubjectId(), moduleId);
        if (meta == null) {
            throw new IllegalArgumentException("模块 ID [" + moduleId + "] 不存在或未配置元数据");
        }

        List<FilterItem> filters = new ArrayList<>();
        Long idFieldId = null;
        if (meta.getFields() != null) {
            for (ModuleFieldDTO f : meta.getFields()) {
                if ("id".equalsIgnoreCase(f.getColumnName())) {
                    idFieldId = f.getId();
                    break;
                }
            }
        }

        if (idFieldId == null) {
            throw new IllegalStateException(
                    String.format(
                            "模块 [%s](ID: %d) 的元数据中未配置主键 [id] 字段，无法执行单据详情查询",
                            meta.getModuleName(), moduleId));
        }

        filters.add(FilterItem.builder().fieldId(idFieldId).operator("EQ").value(id).build());

        QueryReq queryReq =
                QueryReq.builder()
                        .moduleId(moduleId)
                        .pageNo(1)
                        .pageSize(1)
                        .fields(fields)
                        .filters(filters)
                        .build();

        DataPage<Map<String, Object>> page = query(queryReq);
        Map<String, Object> record =
                (page != null && page.getRecords() != null && !page.getRecords().isEmpty())
                        ? page.getRecords().get(0)
                        : Collections.emptyMap();

        return DataDetail.of(meta, record);
    }

    /** 通用字段搜索下拉候选项查询 (基于 QueryPlan 统一编译与执行) */
    public List<DataOption> getOptions(OptionReq req) {
        QueryPlan plan = queryPlanCompiler.compile(req);
        List<String> values = queryPlanExecutor.executeDistinctOptions(plan, req.getFieldId());

        List<DataOption> options = new ArrayList<>();
        for (String val : values) {
            if (val != null) {
                options.add(DataOption.builder().label(val).value(val).build());
            }
        }
        return options;
    }
}
