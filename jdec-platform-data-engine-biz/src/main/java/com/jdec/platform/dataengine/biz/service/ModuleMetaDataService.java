package com.jdec.platform.dataengine.biz.service;

import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.response.ColumnInfoResp;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.dto.context.ModuleQueryContext;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ModuleMetaDataService {
    Map<Long, SysModuleCombineInfo> getModuleMetaDataMap(ModuleQueryContext moduleQueryContext);

    Map<Long, SysModuleCombineInfo> getModuleMetaDataMapAsync(
            ModuleQueryContext moduleQueryContext);

    ModuleTreeDTO getModuleTree(String projectNo, Long subjectId, Long rootModuleId);

    ModuleTreeDTO getModuleTreeAsync(String projectNo, Long subjectId, Long rootModuleId);

    Boolean getApprovalStatusByModuleId(Long moduleId);

    Boolean getApprovalStatusByModuleIdAsync(Long moduleId);

    List<ColumnInfoResp> getTableMetaDataByTabName(String projectNo, String tableName);

    Set<String> getTableMetaFiledDataByTabName(String projectNo, String tableName);
}
