package com.jdec.platform.data.biz.service;

import com.jdec.platform.data.api.dto.request.SaveReq;
import com.jdec.platform.data.biz.plan.compiler.SavePlanCompiler;
import com.jdec.platform.data.biz.plan.executor.SavePlanExecutor;
import com.jdec.platform.data.biz.plan.model.SavePlan;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 动态持久化服务门面 (DynamicPersistenceService)
 *
 * <p>彻底基于 SavePlan 编译与执行模型驱动：
 *
 * <ul>
 *   <li>1. 由 SavePlanCompiler 递归编译保存树并解析父子外键传播规则；
 *   <li>2. 由 SavePlanExecutor 在单事务内执行拓扑有序落库与外键自动回填。
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataPersistenceService {

    private final SavePlanCompiler savePlanCompiler;
    private final SavePlanExecutor savePlanExecutor;

    /** 自相似主子表同构原子保存/更新 (返回根记录 ID) */
    public Long save(SaveReq req) {
        SavePlan plan = savePlanCompiler.compile(req);
        return savePlanExecutor.execute(plan);
    }

    /** 自相似主子表同构原子保存/更新 (返回各个模块的主键映射) */
    public Map<String, Object> saveAndCollectIds(SaveReq req) {
        SavePlan plan = savePlanCompiler.compile(req);
        return savePlanExecutor.executeAndCollectIds(plan);
    }
}
