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
        // 插入时,根模块才会填充这些字段
        if (recordSaveContext.getIsInsert() && recordSaveContext.getIsRootModuleMainTable()) {

            switch (recordSaveContext.getSaveType()) {
                case APPROVAL_SAVE, APPROVAL_CHANGE, APPROVAL_REALTIME -> {
                    fMap.put(
                            "business_no",
                            ToolUtils.nextNoWithFixedLen(recordSaveContext.getTableName()));
                    fMap.put("version_no", 1);
                    fMap.put("business_status", 1);
                }
                case NORMAL_SAVE -> {
                    fMap.put(
                            "business_no",
                            ToolUtils.nextNoWithFixedLen(recordSaveContext.getTableName()));
                    fMap.put("version_no", 1);
                    fMap.put("business_status", 99);
                }
            }
            recordSaveContext.setBizDataMap(fMap);
        }
        return fMap;
    }

    @Override
    public Boolean isReferTo(SaveType type) {
        return true;
    }
}
