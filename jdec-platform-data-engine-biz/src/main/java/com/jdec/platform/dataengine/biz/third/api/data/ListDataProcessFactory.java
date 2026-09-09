package com.jdec.platform.dataengine.biz.third.api.data;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 行数据处理 入口方法 */
@Component
@RequiredArgsConstructor
public class ListDataProcessFactory {

    private final List<ListDataProcessService> dataProcessServiceList;

    public void processData(
            List<Map<String, Object>> dataList,
            Map<Object, Object> returnMap,
            DataIntegrationContext context) {
        if (CollectionUtil.isNotEmpty(dataProcessServiceList)) {
            dataProcessServiceList.stream()
                    .forEach(x -> x.processAssembleData(dataList, returnMap, context));
        }
    }
}
