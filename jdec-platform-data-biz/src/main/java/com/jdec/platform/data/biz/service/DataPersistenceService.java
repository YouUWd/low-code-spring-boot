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
 * <p>基于 SavePlan 编译与执行模型驱动，支持读写 100% 同构的自相似 View 树保存。
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
        if (block != null
                && block.getView() == null
                && meta != null
                && meta.hasModuleTree()
                && meta.moduleTree().getRootModule() != null) {
            block.setView(meta.moduleTree().getRootModule().getModuleCode());
        }
        return DataSaveResp.builder().rootId(rootId).data(block).build();
    }

    /** 自相似主子表同构原子保存/更新 (返回各个模块的主键映射) */
    public Map<String, Object> saveAndCollectIds(DataBlock block) {
        SavePlan plan = savePlanCompiler.compile(block, loadMetaOrNull(block));
        return savePlanExecutor.executeAndCollectIds(plan);
    }

    /** 视图无法解析时返回 null，把「空请求」的错误信息统一交给编译器抛出。 */
    private PlanMeta loadMetaOrNull(DataBlock block) {
        if (block == null) {
            return null;
        }
        String view = block.getView();
        if (view != null && !view.isBlank()) {
            return planMetaLoader.loadByView(view);
        }
        return null;
    }
}
