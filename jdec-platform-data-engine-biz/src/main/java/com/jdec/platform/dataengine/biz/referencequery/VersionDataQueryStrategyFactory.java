package com.jdec.platform.dataengine.biz.referencequery;

import com.jdec.platform.dataengine.biz.context.VersionDataQueryContext;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

// 最新版本数据查询工厂类
@Component
@AllArgsConstructor
public class VersionDataQueryStrategyFactory {

    private List<VersionDataQueryStrategy> STRATEGY_REGISTRY;

    public VersionDataQueryStrategy getVersionDataQueryStrategy(
            VersionDataQueryContext versionDataQueryContext) {
        return STRATEGY_REGISTRY.stream()
                .filter(item -> item.isRefTo(versionDataQueryContext.getBizType()))
                .findFirst()
                .orElse(null);
    }
}
