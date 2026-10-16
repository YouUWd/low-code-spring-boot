package com.jdec.platform.dataengine.biz.third.api.persistence.fill.impl;

import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;
import com.jdec.platform.dataengine.biz.enums.SaveType;
import com.jdec.platform.dataengine.biz.third.api.persistence.fill.BaseDataSaveNecessaryFieldFillService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserContextDataFieldFillServiceImpl implements BaseDataSaveNecessaryFieldFillService {
    @Override
    public Map<String, Object> fillData(RecordSaveContext recordSaveContext) {
        Map<String, Object> fMap = new HashMap<>();
        if (recordSaveContext.getIsInsert()) {
            fMap.put("subject_id", recordSaveContext.getSubjectId());
            fMap.put("created_by", recordSaveContext.getUserId());
            fMap.put("updated_by", recordSaveContext.getUserId());
            fMap.put("created_date", LocalDateTime.now());
            fMap.put("updated_date", LocalDateTime.now());
            fMap.put("deleted", 0);
            if (recordSaveContext.getIsRootModuleMainTable()) {
                fMap.put("active", 1);
            }
            recordSaveContext.setDefaultUserContextDataMap(fMap);
        } else {
            fMap.put("updated_by", recordSaveContext.getUserId());
            fMap.put("updated_date", LocalDateTime.now());
            recordSaveContext.setDefaultUserContextDataMap(fMap);
        }
        return fMap;
    }

    @Override
    public Boolean isReferTo(SaveType type) {
        return true;
    }
}
