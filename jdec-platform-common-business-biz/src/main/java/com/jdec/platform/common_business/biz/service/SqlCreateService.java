package com.jdec.platform.common_business.biz.service;

import com.jdec.platform.common_business.api.dto.common.ModuleField;
import com.jdec.platform.common_business.api.dto.common.ModuleInfo;
import com.jdec.platform.common_business.api.dto.common.ModuleTable;
import java.util.List;

// 动态Sql拼接组装类
public interface SqlCreateService {
    String dynamicQueryDetailData(
            ModuleInfo moduleInfo,
            List<ModuleTable> moduleTables,
            List<ModuleField> moduleFields,
            Long primaryTableDetailId);

    String dynamicQueryListData(
            ModuleInfo moduleInfo, List<ModuleTable> moduleTables, List<ModuleField> moduleFields);
}
