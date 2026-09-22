package com.jdec.platform.dataengine.biz.third.api.data.impl;

import com.jdec.platform.dataengine.api.dto.enums.QueryType;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import com.jdec.platform.dataengine.biz.third.api.data.DetailDataProcessService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/** 详情查看单据版本对比信息获取 */
@Service
@RequiredArgsConstructor
@Order(-99)
@Slf4j
public class HistoryVersionCompareDataProcessServiceImpl implements DetailDataProcessService {

    private final Set<QueryType> TYPE_SET = Set.of(QueryType.DETAIL_APPROVAL_HISTORY);

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return TYPE_SET.contains(queryType);
    }

    @Override
    public void processAssembleData(
            List<NestedModuleDataDTO> records, DataIntegrationContext context) {
        log.debug("历史版本查询版本对比方法触发。");
    }
}
