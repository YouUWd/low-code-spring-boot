package com.jdec.platform.data.biz.plan;

import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.data.api.dto.request.OptionReq;
import com.jdec.platform.data.api.dto.request.QueryReq;
import com.jdec.platform.shared.context.AppContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Plan 元数据快照加载器 (PlanMetaLoader)
 *
 * <p>本类是构建 Plan 链路上**唯一**读取 {@code ConfigApi} 的地方。 在进入业务事务之前先取好快照，再把它作为参数传给编译器，避免元数据读取落在业务事务里。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanMetaLoader {

    private final ConfigApi configApi;

    /**
     * 根据业务视图编码 (view) 加载元数据快照。
     *
     * @param view 业务视图编码 (如 COMPANY, DIRECTORS) 或主表名
     * @return 元数据快照
     */
    public PlanMeta loadByView(String view) {
        if (view == null || view.isBlank()) {
            throw new IllegalArgumentException("加载元数据快照时视图编码不能为空");
        }
        if (view.matches("\\d+")) {
            return load(Long.parseLong(view));
        }
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        List<ModuleTreeDTO> projectTrees = configApi.getProjectModuleTree(projectNo, subjectId);
        if (projectTrees != null) {
            for (ModuleTreeDTO tree : projectTrees) {
                if (tree.getRootModule() != null
                        && (view.equalsIgnoreCase(tree.getRootModule().getModuleCode())
                                || (tree.getRootModule().getPrimaryTable() != null
                                        && view.equalsIgnoreCase(
                                                tree.getRootModule().getPrimaryTable())))) {
                    ProjectTableRelationsDTO relations =
                            configApi.getProjectTableRelations(projectNo);
                    return new PlanMeta(tree, relations);
                }
                if (tree.getModuleMap() != null) {
                    for (ModuleInfo info : tree.getModuleMap().values()) {
                        if (info != null
                                && (view.equalsIgnoreCase(info.getModuleCode())
                                        || (info.getPrimaryTable() != null
                                                && view.equalsIgnoreCase(
                                                        info.getPrimaryTable())))) {
                            ProjectTableRelationsDTO relations =
                                    configApi.getProjectTableRelations(projectNo);
                            return new PlanMeta(tree, relations);
                        }
                    }
                }
            }
        }
        return new PlanMeta(null, null);
    }

    /** 快捷方法：根据 view 加载 */
    public PlanMeta load(String view) {
        return loadByView(view);
    }

    /**
     * 加载指定根模块 ID 的元数据快照（供底层或测试使用）。
     *
     * @param rootModuleId 根模块 ID
     * @return 元数据快照
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

    /** 自动根据 QueryReq 中的 view 或表名加载元数据快照。 */
    public PlanMeta load(QueryReq req) {
        if (req == null) {
            throw new IllegalArgumentException("加载元数据快照时查询请求不能为空");
        }
        String view = req.getView();
        if (view != null && !view.isBlank()) {
            return loadByView(view);
        }
        if (req.getColumns() != null) {
            for (String col : req.getColumns()) {
                if (col != null && col.contains(".")) {
                    String tbl = col.substring(0, col.indexOf('.')).trim();
                    PlanMeta pm = loadByView(tbl);
                    if (pm.hasModuleTree()) {
                        return pm;
                    }
                }
            }
        }
        throw new IllegalArgumentException("查询请求中未提供 view 且无法推导所属元数据");
    }

    /** 自动根据 OptionReq 中的 view 或 tableName 加载元数据快照。 */
    public PlanMeta load(OptionReq req) {
        if (req == null) {
            throw new IllegalArgumentException("加载元数据快照时候选项请求不能为空");
        }
        if (req.getView() != null && !req.getView().isBlank()) {
            return loadByView(req.getView());
        }
        if (req.getTableName() != null && !req.getTableName().isBlank()) {
            return loadByView(req.getTableName());
        }
        throw new IllegalArgumentException("候选项请求中未提供 view 或 tableName");
    }
}
