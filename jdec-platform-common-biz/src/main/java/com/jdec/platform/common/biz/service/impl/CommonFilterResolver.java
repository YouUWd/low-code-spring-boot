package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.approval.api.dto.ApprovalIdButton;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.data.api.dto.request.FilterItem;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
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
 *   <li>通过 {@link FilterSpec} 支持灵活组合系统级过滤条件（【审批引擎】+【数据权限】+【草稿/个人单据】）。
 *   <li>协调 3 大领域条件解析器（{@link ApprovalConditionResolver}、{@link DataPermissionConditionResolver}、
 *       {@link MyDraftConditionResolver}）按需收集 OR 条件分支。
 *   <li>基于 jOOQ 原生执行单条 SQL，获取匹配的主表物理 ID 列表。
 *   <li>将检索出的 ID 集合规范化组装为底层数据引擎支持的 {@link FilterItem}（{@code id IN (:idList) AND active = 1}）。
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommonFilterResolver {

    public static final String ID_COLUMN = "id";
    public static final String ACTIVE_COLUMN = "active";

    private final ApprovalConditionResolver approvalConditionResolver;
    private final MyDraftConditionResolver myDraftConditionResolver;
    private final DataPermissionConditionResolver dataPermissionConditionResolver;
    private final JooqDataContextFactory jooqDataContextFactory;

    /**
     * 根据系统过滤条件组合规格 {@link FilterSpec} 解析主表物理过滤条件
     *
     * @param moduleTree 模块树元数据
     * @param spec 系统过滤组合规格（可自由组合：审批引擎 + 数据权限 + 草稿）
     * @return 主表物理过滤解析结果
     */
    public PrimaryFilterResult resolve(ModuleTreeDTO moduleTree, FilterSpec spec) {
        String scenario = spec != null ? spec.getScenario() : "Unknown";
        if (moduleTree == null || moduleTree.getRootModule() == null || spec == null) {
            log.warn(
                    "[CommonFilterResolver-{}] moduleTree、rootModule 或 filterSpec 为空，跳过主表过滤解析",
                    scenario);
            return PrimaryFilterResult.empty();
        }

        String primaryTable = moduleTree.getPrimaryTable();
        Long rootModuleId = moduleTree.getRootModuleId();
        if (primaryTable == null || primaryTable.isBlank()) {
            log.warn(
                    "[CommonFilterResolver-{}] 主表表名为空，跳过主表过滤条件解析: rootModuleId={}",
                    scenario,
                    rootModuleId);
            return PrimaryFilterResult.empty();
        }

        // 0. 特殊维度：全量有效数据规格（跳过 jOOQ ID 过滤，直接生成主表 active = 1 过滤条件）
        if (spec.isAllActive()) {
            log.info(
                    "[CommonFilterResolver-{}] 当前过滤规格为查看全部有效数据(AllActive)，直接生成主表 active=1 过滤: table={}, rootModuleId={}",
                    scenario,
                    primaryTable,
                    rootModuleId);
            return PrimaryFilterResult.activeOnly(rootModuleId, primaryTable);
        }

        if (!spec.hasEffectiveDimension()) {
            log.info(
                    "[CommonFilterResolver-{}] 当前过滤组合规格无有效过滤维度输入，短路返回空过滤结果: table={}, rootModuleId={}",
                    scenario,
                    primaryTable,
                    rootModuleId);
            return PrimaryFilterResult.empty();
        }

        // 1. 按需收集已启用的系统过滤维度条件分支 (各维度之间为 OR 关系)
        List<Condition> orBranches = new ArrayList<>(3);

        // 1.1 审批引擎分支 (business_no IN (:businessNos))
        Optional<Condition> approvalCond =
                spec.isApprovalEnabled()
                        ? approvalConditionResolver.resolve(spec.getBusinessNos())
                        : Optional.empty();
        approvalCond.ifPresent(orBranches::add);

        // 1.2 行级数据权限分支 (含子表 EXISTS 递归解析)
        Optional<Condition> dataPermCond =
                spec.isDataPermissionEnabled()
                        ? dataPermissionConditionResolver.resolve(
                                moduleTree, spec.getProjectRelations())
                        : Optional.empty();
        dataPermCond.ifPresent(orBranches::add);

        // 1.3 草稿/个人单据分支 (created_by = :userId [AND business_status = :draftStatus])
        Optional<Condition> myDraftCond =
                spec.isDraftEnabled()
                        ? myDraftConditionResolver.resolve(spec.getDraftStatus())
                        : Optional.empty();
        myDraftCond.ifPresent(orBranches::add);

        log.info(
                "[CommonFilterResolver-{}] 解析主表过滤条件: table={}, rootModuleId={}, "
                        + "组合开关=[审批引擎:{}(count={}, samples={}), 数据权限:{}, 草稿/个人单据:{}(status={})], "
                        + "条件分支命中=[审批引擎:{}, 数据权限:{}, 草稿/个人单据:{}]",
                scenario,
                primaryTable,
                rootModuleId,
                spec.isApprovalEnabled(),
                spec.getBusinessNos() != null ? spec.getBusinessNos().size() : 0,
                formatSample(spec.getBusinessNos(), 5),
                spec.isDataPermissionEnabled(),
                spec.isDraftEnabled(),
                spec.getDraftStatus(),
                approvalCond.isPresent(),
                dataPermCond.isPresent(),
                myDraftCond.isPresent());

        // 若所有已启用维度均未生成有效条件，代表当前场景无任何可见数据，直接短路返回空结果
        if (orBranches.isEmpty()) {
            log.info(
                    "[CommonFilterResolver-{}] 已启用的系统过滤维度均无匹配条件，短路返回空过滤结果: table={}, rootModuleId={}",
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
            Field<Long> idField = DSL.field(DSL.name(primaryTable, ID_COLUMN), Long.class);

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

            return PrimaryFilterResult.of(rootModuleId, primaryTable, idList);
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

    /**
     * 系统级过滤条件灵活组合规格
     *
     * <p>支持对【审批引擎】、【数据权限】、【草稿/个人单据】三大系统过滤维度进行任意链式组合。
     */
    @Getter
    @ToString
    @EqualsAndHashCode
    public static final class FilterSpec {

        public static final int DRAFT_STATUS = 1;

        private final String scenario;
        private boolean approvalEnabled;
        private List<String> businessNos = Collections.emptyList();
        private boolean dataPermissionEnabled;
        private ProjectTableRelationsDTO projectRelations;
        private boolean draftEnabled;
        private Integer draftStatus;
        private boolean allActive;

        private FilterSpec(String scenario) {
            this.scenario = (scenario != null && !scenario.isBlank()) ? scenario : "Custom";
        }

        /** 创建指定场景名称的空白过滤组合规格（默认三大维度均关闭，按需链式开启） */
        public static FilterSpec of(String scenario) {
            return new FilterSpec(scenario);
        }

        /** 组合【全部有效数据】过滤：不限创建人与数据权限，仅过滤主表 active = 1 的全部数据 */
        public FilterSpec withAllActive() {
            this.allActive = true;
            return this;
        }

        /** 组合【审批引擎】过滤：匹配指定业务单据号集合 (business_no IN (:businessNos)) */
        public FilterSpec withApproval(Collection<String> businessNos) {
            this.approvalEnabled = true;
            this.businessNos =
                    CollectionUtils.isEmpty(businessNos)
                            ? Collections.emptyList()
                            : List.copyOf(businessNos);
            return this;
        }

        /** 组合【审批引擎】过滤：直接从审批引擎单据按钮列表中提取 business_no 集合 */
        public FilterSpec withApprovalButtons(Collection<ApprovalIdButton> buttons) {
            return withApproval(ApprovalConditionResolver.extractBusinessNos(buttons));
        }

        /** 组合【数据权限】过滤：基于模块树与项目表关联拓扑解析当前角色行级数据权限 */
        public FilterSpec withDataPermission(ProjectTableRelationsDTO projectRelations) {
            this.dataPermissionEnabled = true;
            this.projectRelations = projectRelations;
            return this;
        }

        /** 组合【草稿】过滤：仅匹配当前登录人创建的草稿单据 (created_by = :userId AND business_status = 1) */
        public FilterSpec withDraft() {
            return withDraft(DRAFT_STATUS);
        }

        /**
         * 组合【草稿/个人单据】过滤：匹配当前登录人创建的单据
         *
         * @param draftStatus 状态值（传 1 表示仅草稿；传 null 表示不限状态，即当前登录人创建的全部单据）
         */
        public FilterSpec withDraft(Integer draftStatus) {
            this.draftEnabled = true;
            this.draftStatus = draftStatus;
            return this;
        }

        /** 组合【个人全部单据】过滤：匹配当前登录人创建的所有单据（不限状态） */
        public FilterSpec withMyRecords() {
            return withDraft(null);
        }

        /** 判断当前组合规格是否包含至少一个可产生有效条件的维度（例如仅开启审批过滤但单据号为空时返回 false） */
        public boolean hasEffectiveDimension() {
            if (allActive) {
                return true;
            }
            boolean hasApproval = approvalEnabled && !CollectionUtils.isEmpty(businessNos);
            return hasApproval || dataPermissionEnabled || draftEnabled;
        }
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

        public static PrimaryFilterResult activeOnly(Long rootModuleId, String primaryTable) {
            FilterItem activeFilter = FilterItem.eq(primaryTable, ACTIVE_COLUMN, 1);
            if (rootModuleId != null) {
                activeFilter.setModuleId(rootModuleId);
            }
            return of(List.of(activeFilter), Collections.emptyList());
        }

        public static PrimaryFilterResult of(
                Long rootModuleId, String primaryTable, List<Long> matchedIds) {
            FilterItem idFilter = FilterItem.in(primaryTable, ID_COLUMN, matchedIds);
            FilterItem activeFilter = FilterItem.eq(primaryTable, ACTIVE_COLUMN, 1);
            if (rootModuleId != null) {
                idFilter.setModuleId(rootModuleId);
                activeFilter.setModuleId(rootModuleId);
            }
            return of(List.of(idFilter, activeFilter), matchedIds);
        }

        public boolean isEmpty() {
            return shortCircuitEmpty;
        }
    }
}
