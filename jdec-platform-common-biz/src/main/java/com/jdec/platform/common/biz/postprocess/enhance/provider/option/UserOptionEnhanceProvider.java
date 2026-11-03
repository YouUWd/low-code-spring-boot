package com.jdec.platform.common.biz.postprocess.enhance.provider.option;

import com.jdec.platform.common.api.dto.response.UserSimpleResp;
import com.jdec.platform.common.biz.postprocess.enhance.context.OptionEnhanceContext;
import com.jdec.platform.common.biz.postprocess.enhance.provider.OptionEnhanceProvider;
import com.jdec.platform.common.biz.support.loader.UserInfoLoader;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/** 用户类型候选项增强提供者 (created_by / create_by / user) */
@Component
public class UserOptionEnhanceProvider implements OptionEnhanceProvider {

    private final UserInfoLoader userInfoLoader;

    @Autowired
    public UserOptionEnhanceProvider(UserInfoLoader userInfoLoader) {
        this.userInfoLoader = userInfoLoader;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public boolean supports(OptionEnhanceContext ctx) {
        if (ctx == null) {
            return false;
        }
        String col = ctx.getNormalizedColumn();
        String code = ctx.getBusinessTypeCode();
        return "created_by".equalsIgnoreCase(col)
                || "create_by".equalsIgnoreCase(col)
                || "user".equalsIgnoreCase(code);
    }

    @Override
    public List<DataOptionResp> enhance(List<DataOptionResp> rawOptions, OptionEnhanceContext ctx) {
        if (CollectionUtils.isEmpty(rawOptions)) {
            return rawOptions;
        }

        Set<Long> userIds = new LinkedHashSet<>();
        for (DataOptionResp opt : rawOptions) {
            if (opt != null && opt.getValue() instanceof Number n) {
                userIds.add(n.longValue());
            }
        }

        Map<Long, UserSimpleResp> userMap = userInfoLoader.loadUserMap(userIds);

        for (DataOptionResp opt : rawOptions) {
            if (opt != null && opt.getValue() instanceof Number n) {
                Long uid = n.longValue();
                UserSimpleResp finalUser = UserInfoLoader.orFallback(userMap, uid);
                opt.setValue(finalUser);
                opt.setLabel(finalUser);
            }
        }
        return rawOptions;
    }
}
