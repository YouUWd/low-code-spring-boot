package com.jdec.platform.dataengine.biz.third.api.persistence.fill.impl;

import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;
import com.jdec.platform.dataengine.biz.enums.SaveType;
import com.jdec.platform.dataengine.biz.third.api.persistence.fill.BaseDataSaveNecessaryFieldFillService;
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

        //        // 插入的时候 写入这些字段
        //        if(recordSaveContext.getIsInsert()){
        //            switch (recordSaveContext.getSaveType()) {
        //                //普通保存
        //                case SAVE -> {
        //                    fMap.put("business_no",
        // ToolUtils.nextNoWithFixedLen(recordSaveContext.getTableName()));
        //                    // 主表 设置状态
        //                    if(recordSaveContext.getIsRootModuleMainTable()){
        //                        // 带审批链的状态
        //                        if (recordSaveContext.getIsApproval()) {
        //                            fMap.put("version_no", 1);
        //                            fMap.put("business_status", 1);
        //                        } else {
        //                            // 不带审批链的状态
        //                            fMap.put("version_no", 1);
        //                            fMap.put("business_status", 99);
        //                            fMap.put("registration_time", LocalDateTime.now());
        //                        }
        //                    }else {
        //                        // 非主表 ，版本来源于主表的版本
        //                        fMap.put("version_no",
        // recordSaveContext.getRootModuleMainTableDataVersion());
        //                    }
        //                }
        //                case APPROVAL_CHANGE -> {
        //                    // business_no 不变
        //                    // 变更提交时 version 前面设置
        //                    fMap.put("business_status", 1);
        //                    fMap.put("registration_time", null);
        //                }
        //            }
        //        }
        //        recordSaveContext.setBizDataMap(fMap);
        return fMap;
    }

    @Override
    public Boolean isReferTo(SaveType type) {
        return true;
    }
}
