package com.jdec.platform.data.biz.plan;

import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.shared.context.AppContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Plan 元数据快照加载器 (PlanMetaLoader)
 *
 * <p>本类是构建 Plan 链路上**唯一**读取 {@code ConfigApi} 的地方。之所以把它独立出来，是为了让 「读元数据」与「编译
 * Plan」在时间上解耦：调用方可以在**进入业务事务之前**先取好快照，再把它作为参数 传给编译器，从而避免元数据读取落在业务事务里被事务干扰（读到漂移的配置、无谓拉长事务）。
 *
 * <p>注意：模块树按根模块取，一次操作对应一份快照；同一操作内的多次编译必须复用同一实例。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanMetaLoader {

    private final ConfigApi configApi;

    /**
     * 加载指定根模块的元数据快照（模块树 + 项目表关系）。
     *
     * @param rootModuleId 本次操作的根模块 ID
     * @return 元数据快照；若模块树缺失根模块，{@link PlanMeta#hasModuleTree()} 为 false， 由编译器给出统一的「模块不存在」错误信息
     */
    public PlanMeta load(Long rootModuleId) {
        if (rootModuleId == null) {
            throw new IllegalArgumentException("加载元数据快照时根模块 ID 不能为空");
        }
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        ModuleTreeDTO tree = configApi.getModuleTree(projectNo, subjectId, rootModuleId);
        ProjectTableRelationsDTO relations = configApi.getProjectTableRelations(projectNo);
        return new PlanMeta(tree, relations);
    }
}
