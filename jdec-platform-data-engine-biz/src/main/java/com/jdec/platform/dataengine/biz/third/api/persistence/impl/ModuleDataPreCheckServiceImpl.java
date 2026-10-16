package com.jdec.platform.dataengine.biz.third.api.persistence.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.third.api.persistence.DataPersistenceUtil;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataPreCheckService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleDataPreCheckServiceImpl implements ModuleDataPreCheckService {

    @Override
    public Object preCheck(
            NestedModuleDataDTO param,
            NestedModuleDataDTO base,
            Boolean isOverAllInsert,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        Map<Long, Map<Long, Map<String, Object>>> dataMap = new HashMap<>();
        Map<Long, Map<Long, Set<Long>>> parentToChildDataMap = new HashMap<>();
        if (!isOverAllInsert) {
            covertDataMap(base, 0L, 0L, dataMap, parentToChildDataMap, moduleCacheMap);
        }
        return null;
    }

    private void covertDataMap(
            NestedModuleDataDTO base,
            Long parentModuleId,
            Long parentDataId,
            Map<Long, Map<Long, Map<String, Object>>> dataMap,
            Map<Long, Map<Long, Set<Long>>> parentToChildDataMap,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        if (base == null) {
            return;
        }
        Long currentModuleId = base.getModuleId();

        if (CollectionUtil.isNotEmpty(base.getRecord())) {
            for (RecordDataDTO recordDataDTO : base.getRecord()) {
                // 有行数据
                if (CollectionUtil.isNotEmpty(recordDataDTO.getRow())) {
                    Long dataId =
                            DataPersistenceUtil.getTableRowIdWithTabName(
                                    recordDataDTO.getRow(),
                                    moduleCacheMap
                                            .get(currentModuleId)
                                            .getModuleInfo()
                                            .getPrimaryTable());
                    // 收集数据map
                    dataMap.computeIfAbsent(currentModuleId, k -> new HashMap<>())
                            .computeIfAbsent(dataId, k -> new HashMap<>())
                            .putAll(recordDataDTO.getRow());

                    // 收集父子ID关系
                    parentToChildDataMap
                            .computeIfAbsent(parentModuleId, k -> new HashMap<>())
                            .computeIfAbsent(parentDataId, k -> new HashSet<>())
                            .add(dataId);

                    if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                        for (NestedModuleDataDTO child : recordDataDTO.getChildren()) {
                            covertDataMap(
                                    child,
                                    currentModuleId,
                                    dataId,
                                    dataMap,
                                    parentToChildDataMap,
                                    moduleCacheMap);
                        }
                    }
                } else {
                    // 没有行数据 向下递归
                    if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                        for (NestedModuleDataDTO child : recordDataDTO.getChildren()) {
                            covertDataMap(
                                    child,
                                    currentModuleId,
                                    parentDataId,
                                    dataMap,
                                    parentToChildDataMap,
                                    moduleCacheMap);
                        }
                    }
                }
            }
        }
    }
}
