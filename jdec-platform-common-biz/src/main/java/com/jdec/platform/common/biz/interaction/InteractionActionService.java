package com.jdec.platform.common.biz.interaction;

import com.jdec.platform.common.api.dto.response.ActionButtonDTO;
import com.jdec.platform.common.api.dto.response.ApprovalRowItem;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/**
 * 互动权限操作分发服务 (InteractionActionService)
 *
 * <p>聚合所有生效的 {@link InteractionButtonProvider}，编排生成包含全局与行级绑定的 {@link ApprovalRowItem} 列表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionActionService {

    private final List<InteractionButtonProvider> providers;

    /** 判断当前上下文是否存在任何生效的互动按钮提供者 */
    public boolean hasActiveProviders(InteractionActionContext ctx) {
        if (ctx == null || CollectionUtils.isEmpty(providers)) {
            return false;
        }
        return providers.stream().anyMatch(p -> p.supports(ctx));
    }

    /**
     * 构建模块的互动操作列表 (全局无绑定 + 行绑定操作)
     *
     * @param rawRecords 当前页原始物理数据行列表
     * @param ctx 互动操作上下文
     * @return 互动按钮行项列表，若无有效按钮则返回 null 或空列表
     */
    public List<ApprovalRowItem> buildInteractionActions(
            List<DataRow> rawRecords, InteractionActionContext ctx) {
        if (ctx == null || CollectionUtils.isEmpty(providers)) {
            return null;
        }

        // 1. 过滤当前上下文生效的 Providers (优先采用特定定制 Provider，若无则使用全局兜底 Provider)
        List<InteractionButtonProvider> activeProviders = resolveActiveProviders(ctx);

        if (activeProviders.isEmpty()) {
            return null;
        }

        List<ApprovalRowItem> result = new ArrayList<>();

        // 2. 收集全局互动操作按钮 (与行无关)
        List<ActionButtonDTO> globalBtns = new ArrayList<>();
        for (InteractionButtonProvider provider : activeProviders) {
            provider.addGlobalButtons(ctx, globalBtns);
        }

        if (!globalBtns.isEmpty()) {
            result.add(ApprovalRowItem.ofGlobal(globalBtns));
        }

        // 3. 逐行收集行相关互动操作按钮 (与行数据字段状态关联)
        if (!CollectionUtils.isEmpty(rawRecords)) {
            String primaryTable = ctx.getPrimaryTable();
            for (DataRow rawRow : rawRecords) {
                if (rawRow == null) {
                    continue;
                }
                Long recordId = primaryTable != null ? rawRow.getLong(primaryTable, "id") : null;
                String businessNo =
                        primaryTable != null ? rawRow.getString(primaryTable, "business_no") : null;
                if (businessNo == null || businessNo.isBlank()) {
                    businessNo =
                            primaryTable != null
                                    ? rawRow.getString(primaryTable, "businessNo")
                                    : null;
                }
                if (recordId == null && (businessNo == null || businessNo.isBlank())) {
                    continue;
                }

                List<ActionButtonDTO> rowBtns = new ArrayList<>();
                for (InteractionButtonProvider provider : activeProviders) {
                    provider.addRowButtons(rawRow, ctx, rowBtns);
                }

                if (!rowBtns.isEmpty()) {
                    result.add(ApprovalRowItem.ofRow(recordId, businessNo, rowBtns));
                }
            }
        }

        return result.isEmpty() ? null : result;
    }

    /**
     * 解析当前上下文最终生效的 Provider 列表
     *
     * <p>优先采用精确匹配的业务定制 Provider；若无特定业务定制 Provider，则由通配兜底 Provider ("*") 统一提供。
     */
    protected List<InteractionButtonProvider> resolveActiveProviders(InteractionActionContext ctx) {
        if (ctx == null || CollectionUtils.isEmpty(providers)) {
            return List.of();
        }
        // 1. 寻找非通配的特定业务定制 Provider (如 "company:*" 或 "company:COMPANY-ROOT")
        List<InteractionButtonProvider> specificProviders =
                providers.stream()
                        .filter(p -> p != null && !isWildcard(p.target()) && p.supports(ctx))
                        .toList();
        if (!specificProviders.isEmpty()) {
            return specificProviders;
        }

        // 2. 无特定定制 Provider 时，使用默认全局通配 Provider ("*") 兜底
        return providers.stream()
                .filter(p -> p != null && isWildcard(p.target()) && p.supports(ctx))
                .toList();
    }

    private boolean isWildcard(String target) {
        return target == null || "*".equals(target) || "*:*".equals(target);
    }
}
