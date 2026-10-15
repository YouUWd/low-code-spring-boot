package com.jdec.platform.dataengine.biz.third.api.persistence;

import com.jdec.platform.dataengine.api.dto.request.DynamicDeleteReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicInsertReq;
import com.jdec.platform.dataengine.biz.dto.ModuleDataStoreDTO;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import java.util.List;
import java.util.Map;

public interface ModuleDataPersistenceService {

    public Long save(ModuleDataStoreDTO req);

    Long insertAndGetId(DynamicInsertReq req);

    Long deleteById(DynamicDeleteReq req);

    // 查询主表最大的版本号
    Integer getMaxVersionNoByBusinessNo(String tableName, String businessNo);

    // 获取根模块 主表的ID 递归往上找
    Long getRootModuleMainTableDataId(
            Long id, Long currentModuleId, Map<Long, SysModuleCombineInfo> moduleCacheMap);

    Boolean existRecordInNoneTerminationState(
            String tableName, String businessNo, List<Integer> statusList);
}
