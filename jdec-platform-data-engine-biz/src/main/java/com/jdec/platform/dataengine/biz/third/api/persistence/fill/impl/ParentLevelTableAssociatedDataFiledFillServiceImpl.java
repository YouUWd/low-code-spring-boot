package com.jdec.platform.dataengine.biz.third.api.persistence.fill.impl;

import com.jdec.platform.dataengine.api.dto.model.TableRelationDTO;
import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;
import com.jdec.platform.dataengine.biz.enums.SaveType;
import com.jdec.platform.dataengine.biz.service.TableRelationService;
import com.jdec.platform.dataengine.biz.third.api.persistence.fill.BaseDataSaveNecessaryFieldFillService;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ParentLevelTableAssociatedDataFiledFillServiceImpl
        implements BaseDataSaveNecessaryFieldFillService {

    private final TableRelationService tableRelationService;

    @Override
    public Map<String, Object> fillData(RecordSaveContext recordSaveContext) {
        Map<String, Object> fMap = new HashMap<>();
        if (recordSaveContext.getIsInsert()) {
            if (StringUtils.isEmpty(recordSaveContext.getParentLevelTable())) {
                recordSaveContext.setParentLevelTableAssociationDataMap(fMap);
                return fMap;
            }
            if (recordSaveContext.getParentLevelTableId() == null
                    || recordSaveContext.getParentLevelTableId() <= 0) {
                recordSaveContext.setParentLevelTableAssociationDataMap(fMap);
                return fMap;
            }

            TableRelationDTO relationDTO =
                    tableRelationService.getTableRelation(
                            recordSaveContext.getParentLevelTable(),
                            recordSaveContext.getTableName());

            if (relationDTO != null) {
                fMap.put(relationDTO.getJoinField(), recordSaveContext.getParentLevelTableId());
                recordSaveContext.setParentLevelTableAssociationDataMap(fMap);
                return fMap;
            }
        }
        return fMap;
    }

    @Override
    public Boolean isReferTo(SaveType type) {
        return true;
    }
}
