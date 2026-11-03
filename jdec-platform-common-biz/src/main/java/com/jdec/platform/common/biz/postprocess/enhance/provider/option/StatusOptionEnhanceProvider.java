package com.jdec.platform.common.biz.postprocess.enhance.provider.option;

import com.jdec.platform.common.api.dto.response.StatusSimpleResp;
import com.jdec.platform.common.biz.postprocess.enhance.context.OptionEnhanceContext;
import com.jdec.platform.common.biz.postprocess.enhance.provider.OptionEnhanceProvider;
import com.jdec.platform.common.biz.support.loader.StatusInfoLoader;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/** 业务状态候选项增强提供者 (business_status / status) */
@Component
public class StatusOptionEnhanceProvider implements OptionEnhanceProvider {

    private final StatusInfoLoader statusInfoLoader;

    @Autowired
    public StatusOptionEnhanceProvider(StatusInfoLoader statusInfoLoader) {
        this.statusInfoLoader = statusInfoLoader;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean supports(OptionEnhanceContext ctx) {
        if (ctx == null) {
            return false;
        }
        String col = ctx.getNormalizedColumn();
        String code = ctx.getBusinessTypeCode();
        return "business_status".equalsIgnoreCase(col) || "status".equalsIgnoreCase(code);
    }

    @Override
    public List<DataOptionResp> enhance(List<DataOptionResp> rawOptions, OptionEnhanceContext ctx) {
        if (CollectionUtils.isEmpty(rawOptions)) {
            return rawOptions;
        }

        Set<Integer> statusValues = new LinkedHashSet<>();
        for (DataOptionResp opt : rawOptions) {
            if (opt != null && opt.getValue() instanceof Number n) {
                statusValues.add(n.intValue());
            }
        }

        Long rootModuleId = ctx != null ? ctx.getRootModuleId() : null;
        Map<Integer, StatusSimpleResp> statusMap =
                statusInfoLoader.loadStatusMap(statusValues, rootModuleId);

        for (DataOptionResp opt : rawOptions) {
            if (opt != null && opt.getValue() instanceof Number n) {
                Integer sv = n.intValue();
                StatusSimpleResp finalStatus = StatusInfoLoader.orFallback(statusMap, sv);
                opt.setValue(finalStatus);
                opt.setLabel(finalStatus);
            }
        }
        return rawOptions;
    }
}
