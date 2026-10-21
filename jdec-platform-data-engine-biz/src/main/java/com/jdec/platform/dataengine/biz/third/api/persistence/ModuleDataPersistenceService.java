package com.jdec.platform.dataengine.biz.third.api.persistence;

import com.jdec.platform.dataengine.biz.dto.ModuleDataStoreDTO;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import java.util.List;
import java.util.Map;

public interface ModuleDataPersistenceService {

    Long save(ModuleDataStoreDTO req);

    // 查询主表最大的版本号
    Integer getMaxVersionNoByBusinessNo(String tableName, String businessNo);

    // 获取根模块 主表的ID 递归往上找
    Long getRootModuleMainTableDataId(
            Long id, Long currentModuleId, Map<Long, SysModuleCombineInfo> moduleCacheMap);

    Boolean existRecordInNoneTerminationState(
            String tableName, String businessNo, List<Integer> statusList);
}
