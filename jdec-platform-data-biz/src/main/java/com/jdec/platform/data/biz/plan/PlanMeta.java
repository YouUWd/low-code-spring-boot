package com.jdec.platform.data.biz.plan;

import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;

/**
 * 构建 Plan 所需的元数据快照：模块树 + 项目表关系。
 *
 * <p>编译器（{@code QueryPlanCompiler} / {@code SavePlanCompiler}）只以本快照为输入，自身不再访问 {@code
 * ConfigApi}，因此是纯函数：同一份 Plan 的模块树与表关系必然来自同一次读取，不会因为一次操作里 发生多次读取、或读取跨越事务边界而出现两个不同版本的元数据。
 *
 * <p>约定：由调用方（Service 层）在**进入业务事务之前**通过 {@link PlanMetaLoader} 加载一次，并在整条 链路（详情读取 →
 * 保存）中复用同一实例。模块树按根模块取，因此一次操作对应一份快照。
 */
public record PlanMeta(ModuleTreeDTO moduleTree, ProjectTableRelationsDTO projectRelations) {

    /** 模块树是否可用于编译（根模块必须存在）。 */
    public boolean hasModuleTree() {
        return moduleTree != null && moduleTree.getRootModule() != null;
    }
}
