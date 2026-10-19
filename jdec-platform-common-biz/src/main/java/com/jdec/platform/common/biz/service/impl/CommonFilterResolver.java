package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * 通用底层物理过滤条件解析器 (CommonFilterResolver)
 *
 * <p>核心职责：
 *
 * <ul>
 *   <li>协调 3 大领域条件解析器（{@link ApprovalConditionResolver}、{@link MyDraftConditionResolver}、{@link
 *       DataPermissionConditionResolver}）收集 OR 条件分支。
 *   <li>基于 {@link ModuleTreeDTO} 和 {@link ProjectTableRelationsDTO} 支持根主表及子表行级数据权限。
 *   <li>按语义化场景（待办、已办、全部、默认无审批）提供精准高内聚的过滤解析方法。
 *   <li>基于 jOOQ 原生执行单条 SQL，获取匹配的主表物理 ID 列表。
 *   <li>将检索出的 ID 集合规范化组装为底层数据引擎支持的 {@link FilterItem}（{@code id IN (:idList) AND active = 1}）。
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommonFilterResolver {

    public static final String ACTIVE_COLUMN = "active";

    private final ApprovalConditionResolver approvalConditionResolver;
    private final MyDraftConditionResolver myDraftConditionResolver;
    private final DataPermissionConditionResolver dataPermissionConditionResolver;
    private final JooqDataContextFactory jooqDataContextFactory;

    /**
     * 待办场景过滤解析：[审批引擎待办单据] OR [当前登录人自建草稿 (business_status = 1)]
     *
     * @param moduleTree 模块树元数据
     * @param projectRelations 项目物理表关联拓扑
     * @param todoNos 审批待办单据编号集合
     * @return 主表过滤解析结果
     */
    public PrimaryFilterResult resolveTodoFilters(
            ModuleTreeDTO moduleTree,
            ProjectTableRelationsDTO projectRelations,
            Collection<String> todoNos) {
        return doResolve(moduleTree, projectRelations, todoNos, 1, false, "Todo");
    }

    /**
     * 已办场景过滤解析：仅已办审批单据 (business_no IN (:doneNos))
     *
     * @param moduleTree 模块树元数据
     * @param doneNos 审批已办单据编号集合
     * @return 主表过滤解析结果
     */
    public PrimaryFilterResult resolveDoneFilters(
            ModuleTreeDTO moduleTree, Collection<String> doneNos) {
        if (CollectionUtils.isEmpty(doneNos)) {
            return PrimaryFilterResult.empty();
        }
        return doResolve(moduleTree, null, doneNos, null, false, "Done");
    }

    /**
     * 全部场景过滤解析：[审批引擎全部单据] OR [当前登录人单据 (不限状态)] OR [行级数据权限 (含子表)]
     *
     * @param moduleTree 模块树元数据
     * @param projectRelations 项目物理表关联拓扑
     * @param allNos 审批全部单据编号集合
     * @return 主表过滤解析结果
     */
    public PrimaryFilterResult resolveAllFilters(
            ModuleTreeDTO moduleTree,
            ProjectTableRelationsDTO projectRelations,
            Collection<String> allNos) {
        return doResolve(moduleTree, projectRelations, allNos, null, true, "All");
    }

    /**
     * 默认/无审批流场景过滤解析：[当前登录人单据 (不限状态)] OR [行级数据权限 (含子表)]
     *
     * @param moduleTree 模块树元数据
     * @param projectRelations 项目物理表关联拓扑
     * @return 主表过滤解析结果
     */
    public PrimaryFilterResult resolveDefaultFilters(
            ModuleTreeDTO moduleTree, ProjectTableRelationsDTO projectRelations) {
        return doResolve(
                moduleTree, projectRelations, Collections.emptyList(), null, true, "Default");
    }

    /** 核心统一执行逻辑 */
    private PrimaryFilterResult doResolve(
            ModuleTreeDTO moduleTree,
            ProjectTableRelationsDTO projectRelations,
            Collection<String> businessNos,
            Integer draftStatus,
            boolean includeDataPermission,
            String scenario) {
        if (moduleTree == null || moduleTree.getRootModule() == null) {
            log.warn("[CommonFilterResolver-{}] moduleTree 或 rootModule 为空，跳过主表过滤解析", scenario);
            return PrimaryFilterResult.empty();
        }

        String primaryTable = moduleTree.getRootModule().getPrimaryTable();
        Long rootModuleId = moduleTree.getRootModuleId();

        if (primaryTable == null || primaryTable.isBlank()) {
            log.warn(
                    "[CommonFilterResolver-{}] 主表表名为空，跳过主表过滤条件解析: rootModuleId={}",
                    scenario,
                    rootModuleId);
            return PrimaryFilterResult.empty();
        }

        // 1. 收集 3 大业务维度的条件分支 (OR 关系)
        List<Condition> orBranches = new ArrayList<>();

        // 1.1 审批链单据分支
        Optional<Condition> approvalCond = approvalConditionResolver.resolve(businessNos);
        approvalCond.ifPresent(orBranches::add);

        // 1.2 创建人/草稿分支 (仅当指定了 draftStatus 或为全部/默认场景时纳入当前人分支)
        Optional<Condition> myDraftCond = Optional.empty();
        if (draftStatus != null
                || "All".equalsIgnoreCase(scenario)
                || "Default".equalsIgnoreCase(scenario)) {
            myDraftCond = myDraftConditionResolver.resolve(draftStatus);
            myDraftCond.ifPresent(orBranches::add);
        }

        // 1.3 行级数据权限分支 (含子表 EXISTS 递归解析)
        Optional<Condition> dataPermCond = Optional.empty();
        if (includeDataPermission) {
            dataPermCond = dataPermissionConditionResolver.resolve(moduleTree, projectRelations);
            dataPermCond.ifPresent(orBranches::add);
        }

        log.info(
                "[CommonFilterResolver-{}] 开始解析主表过滤条件: table={}, rootModuleId={}, "
                        + "businessNos(count={}, samples={}), draftStatus={}, includeDataPermission={}, "
                        + "条件分支命中=[审批链:{}, 我的草稿/单据:{}, 数据权限:{}]",
                scenario,
                primaryTable,
                rootModuleId,
                businessNos != null ? businessNos.size() : 0,
                formatSample(businessNos, 5),
                draftStatus,
                includeDataPermission,
                approvalCond.isPresent(),
                myDraftCond.isPresent(),
                dataPermCond.isPresent());

        // 若三大业务维度均未提供有效条件，代表该场景下没有任何数据可被查看，直接短路为空
        if (orBranches.isEmpty()) {
            log.info(
                    "[CommonFilterResolver-{}] 三大业务维度均未提供有效过滤条件，短路返回空过滤结果: table={}, rootModuleId={}",
                    scenario,
                    primaryTable,
                    rootModuleId);
            return PrimaryFilterResult.empty();
        }

        // 2. 组合最终 WHERE 子句：active = 1 AND (branch1 OR branch2 OR ...)
        Condition finalCondition =
                DSL.field(DSL.name(primaryTable, ACTIVE_COLUMN), Integer.class)
                        .eq(1)
                        .and(DSL.or(orBranches));

        // 3. 执行单条 jOOQ 原生查询，获取满足条件的主表物理主键 ID 集合
        long startTime = System.currentTimeMillis();
        try {
            DSLContext dsl = jooqDataContextFactory.getContext();
            Field<Long> idField = DSL.field(DSL.name(primaryTable, "id"), Long.class);

            List<Long> idList =
                    dsl.selectDistinct(idField)
                            .from(DSL.table(DSL.name(primaryTable)))
                            .where(finalCondition)
                            .fetchInto(Long.class);

            long costMs = System.currentTimeMillis() - startTime;

            if (CollectionUtils.isEmpty(idList)) {
                log.info(
                        "[CommonFilterResolver-{}] jOOQ查询完成: table={}, rootModuleId={}, 耗时={}ms, 匹配ID数=0, SQL条件={}",
                        scenario,
                        primaryTable,
                        rootModuleId,
                        costMs,
                        finalCondition);
                return PrimaryFilterResult.empty();
            }

            log.info(
                    "[CommonFilterResolver-{}] jOOQ查询完成: table={}, rootModuleId={}, 耗时={}ms, 匹配ID数={}, 匹配ID样例={}, SQL条件={}",
                    scenario,
                    primaryTable,
                    rootModuleId,
                    costMs,
                    idList.size(),
                    formatSample(idList, 10),
                    finalCondition);

            // 4. 组装输出 FilterItem：主表 id IN (:idList) AND 主表 active = 1
            FilterItem idFilter = FilterItem.in(primaryTable, "id", idList);
            FilterItem activeFilter = FilterItem.eq(primaryTable, ACTIVE_COLUMN, 1);
            if (rootModuleId != null) {
                idFilter.setModuleId(rootModuleId);
                activeFilter.setModuleId(rootModuleId);
            }
            return PrimaryFilterResult.of(List.of(idFilter, activeFilter), idList);
        } catch (Exception ex) {
            long costMs = System.currentTimeMillis() - startTime;
            log.error(
                    "[CommonFilterResolver-{}] 执行 jOOQ 权限过滤查询失败: table={}, rootModuleId={}, 耗时={}ms, SQL条件={}, err={}",
                    scenario,
                    primaryTable,
                    rootModuleId,
                    costMs,
                    finalCondition,
                    ex.getMessage(),
                    ex);
            return PrimaryFilterResult.empty();
        }
    }

    /**
     * 向后兼容快捷方法：旧版 6 参数方法
     *
     * @deprecated 请使用语义化场景方法：{@link #resolveTodoFilters}、{@link #resolveDoneFilters}、{@link
     *     #resolveAllFilters} 或 {@link #resolveDefaultFilters}
     */
    @Deprecated
    public PrimaryFilterResult resolvePrimaryFilters(
            String primaryTable,
            Long rootModuleId,
            Collection<String> businessNos,
            Long createdBy,
            Integer businessStatus,
            boolean includeDataPermission) {
        ModuleTreeDTO dummyTree =
                ModuleTreeDTO.builder()
                        .rootModuleId(rootModuleId)
                        .rootModule(
                                ModuleInfo.builder()
                                        .moduleId(rootModuleId)
                                        .primaryTable(primaryTable)
                                        .build())
                        .build();
        return doResolve(
                dummyTree, null, businessNos, businessStatus, includeDataPermission, "Legacy");
    }

    /** 格式化集合样例，防止大量数据日志刷屏 */
    public static String formatSample(Collection<?> collection, int max) {
        if (CollectionUtils.isEmpty(collection)) {
            return "[]";
        }
        if (collection.size() <= max) {
            return collection.toString();
        }
        return collection.stream().limit(max).toList() + " (共" + collection.size() + "项)";
    }

    /** 向后兼容快捷方法：委托给 {@link DataPermissionConditionResolver} 解析行级数据权限条件 */
    public Condition buildDataPermissionCondition(String primaryTable) {
        return dataPermissionConditionResolver.resolve(primaryTable).orElse(null);
    }

    /** 主表物理过滤解析结果 */
    public record PrimaryFilterResult(
            List<FilterItem> filters, boolean shortCircuitEmpty, List<Long> matchedIds) {
        public static PrimaryFilterResult empty() {
            return new PrimaryFilterResult(Collections.emptyList(), true, Collections.emptyList());
        }

        public static PrimaryFilterResult of(List<FilterItem> filters, List<Long> matchedIds) {
            return new PrimaryFilterResult(filters, false, matchedIds);
        }

        public boolean isEmpty() {
            return shortCircuitEmpty;
        }
    }
}
