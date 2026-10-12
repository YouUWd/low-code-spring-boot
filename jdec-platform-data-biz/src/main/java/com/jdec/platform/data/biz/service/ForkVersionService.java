package com.jdec.platform.data.biz.service;

import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.request.ForkVersionReq;
import com.jdec.platform.data.api.dto.request.TableDetailQueryReq;
import com.jdec.platform.data.api.dto.response.DataSaveResp;
import com.jdec.platform.data.biz.plan.PlanMeta;
import com.jdec.platform.data.biz.plan.PlanMetaLoader;
import com.jdec.platform.data.biz.util.DocumentVersionUtils;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

/**
 * 单据版本派生与局部增量融合服务 (ForkVersionService)
 *
 * <p>职责划分与事务边界：
 *
 * <ul>
 *   <li>1. 元数据快照加载 (PlanMetaLoader -> ConfigApi) 在独立事务或无事务环境中执行，杜绝跨服务/配置中心远程调用被包裹在大事务内产生嵌套或长事务问题；
 *   <li>2. 读侧通过 DataQueryService 基于该元数据快照抓取基准单据完整树；
 *   <li>3. 在内存中完成 deepMerge、stripIdsAndResetAudit 以及版本号计算；
 *   <li>4. 写侧委托 DataPersistenceService.save(merged, meta) 执行原子落库（落库事务由底层 SavePlanExecutor 严格收敛与控制）。
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForkVersionService {

    private final DataQueryService dataQueryService;
    private final DataPersistenceService dataPersistenceService;
    private final JooqDataContextFactory jooqDataContextFactory;
    private final PlanMetaLoader planMetaLoader;

    /**
     * 单据版本派生与局部增量融合
     *
     * @param req 版本派生请求参数
     * @return 派生后生成的新单据根记录主键 ID
     */
    public Long forkNewVersion(ForkVersionReq req) {
        if (req == null || req.getModuleId() == null || req.getBaseId() == null) {
            throw new IllegalArgumentException(
                    "moduleId and baseId cannot be null for forkNewVersion");
        }

        // 0. 在事务外一次性取好元数据快照 (无业务事务包裹 ConfigApi 远程/本地调用)，
        //    整条链路（详情读取 → 保存）复用同一份快照，避免元数据在事务内重复读取而漂移
        PlanMeta meta = planMetaLoader.load(req.getModuleId());

        // 1. 抓取基准单据完整物理快照 (含伴生表与所有子表全部列)
        DataBlock baseBlock =
                dataQueryService.getDetailByTable(
                        TableDetailQueryReq.builder()
                                .moduleId(req.getModuleId())
                                .id(req.getBaseId())
                                .build(),
                        meta);
        if (baseBlock == null || baseBlock.isEmpty()) {
            throw new IllegalArgumentException("Base record not found for id: " + req.getBaseId());
        }

        // 2. 深度合并局部增量数据
        DataBlock merged = DocumentVersionUtils.deepMerge(baseBlock, req.getPatchData());

        // 3. 递归清洗树主键 ID 与关联外键，重置审计字段
        DocumentVersionUtils.stripIdsAndResetAudit(merged);

        // 4. 从数据快照自解析主表名与 business_no，就地查询 MAX(version_no) + 1 并在整树下发
        String businessNo = DocumentVersionUtils.extractBusinessNo(baseBlock);
        String primaryTable = DocumentVersionUtils.extractPrimaryTable(baseBlock);
        if (businessNo != null
                && !businessNo.isBlank()
                && primaryTable != null
                && !primaryTable.isBlank()) {
            Integer nextVersion = computeNextVersion(primaryTable, businessNo);
            DocumentVersionUtils.applyVersionNo(merged, nextVersion);
            log.info(
                    "版本派生/克隆: 主表={}, 单据号={}, 原始基准ID={}, 计算生成最新版本号={}",
                    primaryTable,
                    businessNo,
                    req.getBaseId(),
                    nextVersion);
        }

        // 5. 调用底层保存引擎落库生成新版本（由 SavePlanExecutor 内部的 @Transactional 保证落库原子性）
        DataSaveResp saveResp = dataPersistenceService.save(merged, meta);
        return saveResp != null ? saveResp.getRootId() : null;
    }

    private Integer computeNextVersion(String primaryTable, String businessNo) {
        DSLContext dsl = jooqDataContextFactory.getContext();
        Integer maxVersion = null;
        try {
            maxVersion =
                    dsl.select(
                                    DSL.max(
                                            DSL.field(
                                                    DSL.name(primaryTable, "version_no"),
                                                    Integer.class)))
                            .from(DSL.table(DSL.name(primaryTable)))
                            .where(DSL.field(DSL.name(primaryTable, "business_no")).eq(businessNo))
                            .fetchOne(0, Integer.class);
        } catch (Exception ex) {
            log.warn("查询主表 [{}] 单据 [{}] 最大版本号失败: {}", primaryTable, businessNo, ex.getMessage());
        }

        return (maxVersion != null ? maxVersion : 0) + 1;
    }
}
