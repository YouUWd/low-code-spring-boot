package com.jdec.platform.dataengine.biz.third.api.data;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 详情数据处理 */
@Component
@RequiredArgsConstructor
public class DetailDataProcessFactory {
    private final List<DetailDataProcessService> dataProcessServiceList;

    public void processData(List<NestedModuleDataDTO> moduleData, DataIntegrationContext context) {
        if (CollectionUtil.isNotEmpty(dataProcessServiceList)) {
            dataProcessServiceList.stream()
                    .filter(x -> x.isReferTo(context.getQueryType()))
                    .forEach(x -> x.processAssembleData(moduleData, context));
        }
    }
}
