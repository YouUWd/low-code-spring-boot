package com.jdec.platform.common.biz.interaction;

import com.jdec.platform.common.api.dto.response.ActionButtonDTO;
import com.jdec.platform.common.api.dto.response.ActionRowItem;
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
 * <p>聚合所有生效的 {@link InteractionButtonProvider}，编排生成包含全局与行级绑定的 {@link ActionRowItem} 列表。
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
     * 收集模块的全局互动操作按钮 (供页面顶部工具栏直接渲染)
     *
     * @param ctx 互动操作上下文
     * @return 全局互动按钮列表，若无有效按钮则返回 null
     */
    public List<ActionButtonDTO> buildGlobalActions(InteractionActionContext ctx) {
        if (ctx == null || CollectionUtils.isEmpty(providers)) {
            return null;
        }

        List<InteractionButtonProvider> activeProviders = resolveActiveProviders(ctx);
        if (activeProviders.isEmpty()) {
            return null;
        }

        List<ActionButtonDTO> globalBtns = new ArrayList<>();
        for (InteractionButtonProvider provider : activeProviders) {
            provider.addGlobalButtons(ctx, globalBtns);
        }

        return globalBtns.isEmpty() ? null : globalBtns;
    }

    /**
     * 收集模块的行级互动操作列表 (纯行级单据操作，严格保证包含 id 与 businessNo)
     *
     * @param rawRecords 当前页原始物理数据行列表
     * @param ctx 互动操作上下文
     * @return 纯行级互动按钮行项列表，若无有效按钮则返回 null
     */
    public List<ActionRowItem> buildRowActions(
            List<DataRow> rawRecords, InteractionActionContext ctx) {
        if (ctx == null
                || CollectionUtils.isEmpty(providers)
                || CollectionUtils.isEmpty(rawRecords)) {
            return null;
        }

        List<InteractionButtonProvider> activeProviders = resolveActiveProviders(ctx);
        if (activeProviders.isEmpty()) {
            return null;
        }

        List<ActionRowItem> result = new ArrayList<>();
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
                        primaryTable != null ? rawRow.getString(primaryTable, "businessNo") : null;
            }
            if (recordId == null && (businessNo == null || businessNo.isBlank())) {
                continue;
            }

            List<ActionButtonDTO> rowBtns = new ArrayList<>();
            for (InteractionButtonProvider provider : activeProviders) {
                provider.addRowButtons(rawRow, ctx, rowBtns);
            }

            if (!rowBtns.isEmpty()) {
                result.add(ActionRowItem.ofRow(recordId, businessNo, rowBtns));
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
