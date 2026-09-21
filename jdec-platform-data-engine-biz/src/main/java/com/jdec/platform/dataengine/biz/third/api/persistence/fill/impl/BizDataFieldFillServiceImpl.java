package com.jdec.platform.dataengine.biz.third.api.persistence.fill.impl;

import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;
import com.jdec.platform.dataengine.biz.enums.SaveType;
import com.jdec.platform.dataengine.biz.third.api.persistence.fill.BaseDataSaveNecessaryFieldFillService;
import com.jdec.platform.dataengine.biz.util.ToolUtils;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class BizDataFieldFillServiceImpl implements BaseDataSaveNecessaryFieldFillService {
    @Override
    public Map<String, Object> fillData(RecordSaveContext recordSaveContext) {
        Map<String, Object> fMap = new HashMap<>();
        if (recordSaveContext.getIsInsert() && recordSaveContext.getIsRootModuleMainTable()) {
            fMap.put("business_no", ToolUtils.nextNoWithFixedLen(recordSaveContext.getTableName()));
            fMap.put("business_status", 0);
            fMap.put("version_no", 1);
            fMap.put("module_id", recordSaveContext.getSysModuleCompleteResp().getModuleId());
            recordSaveContext.setBizDataMap(fMap);
        }
        return fMap;
    }

    @Override
    public Boolean isReferTo(SaveType type) {
        return true;
    }
}
