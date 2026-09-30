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
        // 模块定义 根模块才会填充这些字段
        if (recordSaveContext.getIsRootModuleMainTable()) {
            // 插入时
            if (recordSaveContext.getIsInsert()) {
                switch (recordSaveContext.getSaveType()) {
                    case APPROVAL_SAVE -> {
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
            } else {
                // 更新时候，只有变更保存才会 重置 version_no business_status,registration_time

                switch (recordSaveContext.getSaveType()) {
                    case APPROVAL_CHANGE -> {
                        Number versionNo =
                                (Number) recordSaveContext.getReadOutRecord().get("version_no");
                        Integer v = versionNo.intValue() + 1;
                        fMap.put("version_no", v);
                        fMap.put("business_status", 1);
                        fMap.put("registration_time", null);
                    }
                }
            }
        }
        return fMap;
    }

    @Override
    public Boolean isReferTo(SaveType type) {
        return true;
    }
}
