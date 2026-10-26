package com.jdec.platform.data.biz.service;

import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.response.DataSaveResp;
import com.jdec.platform.data.biz.plan.PlanMeta;
import com.jdec.platform.data.biz.plan.PlanMetaLoader;
import com.jdec.platform.data.biz.plan.compiler.SavePlanCompiler;
import com.jdec.platform.data.biz.plan.executor.SavePlanExecutor;
import com.jdec.platform.data.biz.plan.model.SavePlan;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 动态持久化服务门面 (DataPersistenceService)
 *
 * <p>彻底基于 SavePlan 编译与执行模型驱动：
 *
 * <ul>
 *   <li>1. 由 PlanMetaLoader 取一份元数据快照（模块树 + 表关系）；
 *   <li>2. 由 SavePlanCompiler 以该快照递归编译保存树并解析父子外键传播规则；
 *   <li>3. 由 SavePlanExecutor 在单事务内执行拓扑有序落库与外键自动回填；
 *   <li>4. 返回强类型同构的 DataSaveResp。
 * </ul>
 *
 * <p>元数据快照在**编译前**取好，事务只覆盖落库（{@link SavePlanExecutor} 上的 {@code @Transactional}）， 因此编译过程不会被业务事务干扰。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataPersistenceService {

    private final SavePlanCompiler savePlanCompiler;
    private final SavePlanExecutor savePlanExecutor;
    private final PlanMetaLoader planMetaLoader;

    /** 自相似主子表同构原子保存/更新 (返回 DataSaveResp，包含根记录 ID 与回填了主键后的数据树) */
    public DataSaveResp save(DataBlock block) {
        return save(block, loadMetaOrNull(block));
    }

    /** 自相似主子表同构原子保存/更新（复用调用方已加载的元数据快照） */
    public DataSaveResp save(DataBlock block, PlanMeta meta) {
        SavePlan plan = savePlanCompiler.compile(block, meta);
        Long rootId = savePlanExecutor.execute(plan);
        return DataSaveResp.builder().rootId(rootId).data(block).build();
    }

    /** 自相似主子表同构原子保存/更新 (返回各个模块的主键映射) */
    public Map<String, Object> saveAndCollectIds(DataBlock block) {
        SavePlan plan = savePlanCompiler.compile(block, loadMetaOrNull(block));
        return savePlanExecutor.executeAndCollectIds(plan);
    }

    /** 根模块 ID 无法解析时返回 null，把「空请求」的错误信息统一交给编译器抛出。 */
    private PlanMeta loadMetaOrNull(DataBlock block) {
        Long moduleId = SavePlanCompiler.resolveRootModuleId(block);
        return moduleId != null ? planMetaLoader.load(moduleId) : null;
    }
}
