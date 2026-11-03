package com.jdec.platform.common.biz.postprocess.enhance.provider.row;

import com.jdec.platform.common.api.dto.response.StatusSimpleResp;
import com.jdec.platform.common.biz.postprocess.enhance.context.DataRowEnhanceContext;
import com.jdec.platform.common.biz.postprocess.enhance.provider.DataRowEnhanceProvider;
import com.jdec.platform.common.biz.support.loader.StatusInfoLoader;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/** 业务状态字段数据行增强提供者 (business_status) */
@Component
@RequiredArgsConstructor
public class StatusRowEnhanceProvider implements DataRowEnhanceProvider {

    private static final String COLUMN = "business_status";

    private final StatusInfoLoader statusInfoLoader;

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean supports(DataRowEnhanceContext ctx) {
        return ctx != null && StringUtils.hasText(ctx.getPrimaryTable());
    }

    @Override
    public void enhance(List<DataRow> records, DataRowEnhanceContext ctx) {
        if (CollectionUtils.isEmpty(records)) {
            return;
        }
        String table = ctx.getPrimaryTable();

        // 阶段 1: 扫描收集业务状态 statusValues
        Set<Integer> statusValues = new HashSet<>();
        for (DataRow row : records) {
            Integer sv = row != null ? row.getInteger(table, COLUMN) : null;
            if (sv != null) {
                statusValues.add(sv);
            }
        }
        if (statusValues.isEmpty()) {
            return;
        }

        // 阶段 2: 批量加载并回填结构化对象
        Map<Integer, StatusSimpleResp> statusMap =
                statusInfoLoader.loadStatusMap(statusValues, ctx.getRootModuleId());
        for (DataRow row : records) {
            Integer sv = row != null ? row.getInteger(table, COLUMN) : null;
            if (sv != null) {
                row.putValue(table, COLUMN, StatusInfoLoader.orFallback(statusMap, sv));
            }
        }
    }
}
