package com.jdec.platform.common.biz.custom;

import com.jdec.platform.common.api.dto.response.ApprovalRowItem;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * 行级定制操作分发器 (RowCustomActionService)
 *
 * <p>自动发现并聚合所有注入的 {@link CustomButtonProvider}，按行执行按钮收集并组装为 {@link ApprovalRowItem} 列表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RowCustomActionService {

    private final List<CustomButtonProvider> providers;

    /** 判断当前上下文是否存在任何生效的定制按钮提供者 */
    public boolean hasActiveProviders(CustomActionContext ctx) {
        if (ctx == null || CollectionUtils.isEmpty(providers)) {
            return false;
        }
        return providers.stream().anyMatch(p -> p.supports(ctx));
    }

    /**
     * 为多行数据构建定制操作行列表
     *
     * @param rawRecords 原始物理数据行列表
     * @param ctx 定制操作上下文
     * @return 包含 id 与 btnList 的定制行操作列表，若无有效按钮则返回 null 或空列表
     */
    public List<ApprovalRowItem> buildCustomActions(
            List<DataRow> rawRecords, CustomActionContext ctx) {
        if (ctx == null || CollectionUtils.isEmpty(providers)) {
            log.info(
                    "[CustomAction] 跳过定制操作装配: ctx={}, providersCount={}",
                    ctx != null,
                    providers != null ? providers.size() : 0);
            return null;
        }

        // 1. 过滤当前上下文生效的 Providers
        List<CustomButtonProvider> activeProviders =
                providers.stream().filter(p -> p.supports(ctx)).toList();

        if (activeProviders.isEmpty()) {
            log.info(
                    "[CustomAction] 当前模块无生效的定制Provider: projectNo={}, rootModuleId={}, enableActive={}, hasEditPermission={}",
                    ctx.projectNo(),
                    ctx.rootModuleId(),
                    ctx.enableActive(),
                    ctx.hasEditPermission());
            return null;
        }

        log.info(
                "[CustomAction] 匹配到生效的定制Provider: count={}, providers={}, 待装配行数={}",
                activeProviders.size(),
                activeProviders.stream().map(p -> p.getClass().getSimpleName()).toList(),
                rawRecords != null ? rawRecords.size() : 0);

        if (CollectionUtils.isEmpty(rawRecords)) {
            return Collections.emptyList();
        }

        // 2. 逐行装配定制按钮
        List<ApprovalRowItem> result = new ArrayList<>(rawRecords.size());
        String primaryTable = ctx.primaryTable();

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

            List<Map<String, Object>> btns = new ArrayList<>();
            for (CustomButtonProvider provider : activeProviders) {
                provider.addButtons(rawRow, ctx, btns);
            }

            if (!btns.isEmpty()) {
                Map<String, Object> rowPayload = new LinkedHashMap<>();
                if (recordId != null) {
                    rowPayload.put("id", recordId);
                }
                if (businessNo != null && !businessNo.isBlank()) {
                    rowPayload.put("businessNo", businessNo);
                }
                rowPayload.put("btnList", btns);
                result.add(ApprovalRowItem.builder().row(rowPayload).build());
            }
        }
        return result;
    }
}
