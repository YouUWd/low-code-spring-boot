package com.jdec.platform.common.biz.postprocess.enhance.provider.row;

import com.jdec.platform.common.api.dto.response.UserSimpleResp;
import com.jdec.platform.common.biz.postprocess.enhance.context.DataRowEnhanceContext;
import com.jdec.platform.common.biz.postprocess.enhance.provider.DataRowEnhanceProvider;
import com.jdec.platform.common.biz.support.loader.UserInfoLoader;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/** 用户字段数据行增强提供者 (created_by) */
@Component
@RequiredArgsConstructor
public class UserRowEnhanceProvider implements DataRowEnhanceProvider {

    private static final String COLUMN = "created_by";

    private final UserInfoLoader userInfoLoader;

    @Override
    public int order() {
        return 10;
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

        // 阶段 1: 扫描收集创建人 userIds
        Set<Long> userIds = new HashSet<>();
        for (DataRow row : records) {
            Long uid = row != null ? row.getLong(table, COLUMN) : null;
            if (uid != null) {
                userIds.add(uid);
            }
        }
        if (userIds.isEmpty()) {
            return;
        }

        // 阶段 2: 批量加载并回填结构化对象
        Map<Long, UserSimpleResp> userMap = userInfoLoader.loadUserMap(userIds);
        for (DataRow row : records) {
            Long uid = row != null ? row.getLong(table, COLUMN) : null;
            if (uid != null) {
                row.putValue(table, COLUMN, UserInfoLoader.orFallback(userMap, uid));
            }
        }
    }
}
