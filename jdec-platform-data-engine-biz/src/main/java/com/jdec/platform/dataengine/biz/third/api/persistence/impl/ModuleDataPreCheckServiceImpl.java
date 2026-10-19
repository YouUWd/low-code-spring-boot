package com.jdec.platform.dataengine.biz.third.api.persistence.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.third.api.persistence.DataPersistenceUtil;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataPreCheckService;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleDataPreCheckServiceImpl implements ModuleDataPreCheckService {

    private final Set<String> UNCHECK_PERMISSION_FIELDS =
            Set.of(
                    "id",
                    "subject_id",
                    "business_no",
                    "version_no",
                    "business_status",
                    "created_by",
                    "updated_by",
                    "created_date",
                    "updated_date",
                    "deleted",
                    "active",
                    "source_id");

    @Override
    public Boolean prePermissionCheck(
            NestedModuleDataDTO param,
            NestedModuleDataDTO base,
            Boolean isOverAllInsert,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        Map<Long, Map<Long, Map<String, Object>>> dataMap = new HashMap<>();
        Map<Long, Map<Long, Set<Long>>> parentToChildDataMap = new HashMap<>();
        if (!isOverAllInsert) {
            covertDataMap(base, 0L, 0L, dataMap, parentToChildDataMap, moduleCacheMap);
        }
        Map<Long, Map<String, Set<String>>> result =
                checkPermission(param, dataMap, parentToChildDataMap, moduleCacheMap);
        if (CollectionUtil.isEmpty(result)) {
            return true;
        }
        List<String> messages = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Set<String>>> entry : result.entrySet()) {
            StringBuilder sb = new StringBuilder();
            sb.append("模块[").append(entry.getKey()).append("],");
            if (entry.getValue().containsKey("apply")) {
                sb.append("没有申请权限字段:");
                sb.append(
                        entry.getValue().get("apply").stream()
                                .map(s -> s + " ")
                                .collect(Collectors.joining(",")));
            }
            if (entry.getValue().containsKey("edit")) {
                sb.append("没有编辑权限字段:");
                sb.append(
                        entry.getValue().get("edit").stream()
                                .map(s -> s + " ")
                                .collect(Collectors.joining(",")));
            }
            messages.add(sb.toString());
        }
        throw new BusinessException(
                ApiResponseCodeEnum.BAD_MODULE_FIELD_APPLY_RIGHT_ERROR.getCode(),
                StringUtils.join(messages));
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

    private Map<Long, Map<String, Set<String>>> checkPermission(
            NestedModuleDataDTO param,
            Map<Long, Map<Long, Map<String, Object>>> dataMap,
            Map<Long, Map<Long, Set<Long>>> parentToChildDataMap,
            Map<Long, SysModuleCombineInfo> moduleCacheMap) {
        Map<Long, Map<String, Set<String>>> result = new HashMap<>();
        if (param == null) {
            return result;
        }
        Long currentModuleId = param.getModuleId();
        SysModuleCombineInfo sysModuleCombineInfo = moduleCacheMap.get(currentModuleId);
        if (CollectionUtil.isEmpty(param.getRecord())) {
            return result;
        }
        // 根节点只有一行数据
        RecordDataDTO recordDataDTO = param.getRecord().get(0);
        Long dataId =
                DataPersistenceUtil.getTableRowIdWithTabName(
                        recordDataDTO.getRow(),
                        moduleCacheMap.get(currentModuleId).getModuleInfo().getPrimaryTable());
        checkRow(
                recordDataDTO,
                dataMap,
                sysModuleCombineInfo,
                dataId == null || dataId <= 0,
                result);
        if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
            for (NestedModuleDataDTO child : recordDataDTO.getChildren()) {
                // 如果 父节点是一个新增的行，那么子节点 都是新增的行
                checkNestedDataPermission(
                        child,
                        dataMap,
                        parentToChildDataMap,
                        currentModuleId,
                        dataId,
                        dataId == null || dataId <= 0,
                        moduleCacheMap,
                        result);
            }
        }
        return result;
    }

    private void checkNestedDataPermission(
            NestedModuleDataDTO param,
            Map<Long, Map<Long, Map<String, Object>>> dataMap,
            Map<Long, Map<Long, Set<Long>>> parentToChildDataMap,
            Long parentModuleId,
            Long parentDataId,
            Boolean isOverAllInsert,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            Map<Long, Map<String, Set<String>>> result) {
        if (param == null) {
            return;
        }
        Long currentModuleId = param.getModuleId();
        SysModuleCombineInfo sysModuleCombineInfo = moduleCacheMap.get(currentModuleId);

        // 虚模块 直接向下递归
        if (StringUtils.isEmpty(sysModuleCombineInfo.getModuleInfo().getPrimaryTable())) {
            if (CollectionUtil.isNotEmpty(param.getRecord())) {
                for (RecordDataDTO recordDataDTO : param.getRecord()) {
                    if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                        for (NestedModuleDataDTO child : recordDataDTO.getChildren()) {
                            checkNestedDataPermission(
                                    child,
                                    dataMap,
                                    parentToChildDataMap,
                                    currentModuleId,
                                    parentDataId,
                                    isOverAllInsert,
                                    moduleCacheMap,
                                    result);
                        }
                    }
                }
            }
        }

        // 收集当前 模块 需要更新的ID
        Set<Long> updateDataIds = new HashSet<>();
        for (RecordDataDTO recordDataDTO : param.getRecord()) {
            if (CollectionUtil.isNotEmpty(recordDataDTO.getRow())) {
                Long dataId =
                        DataPersistenceUtil.getTableRowIdWithTabName(
                                recordDataDTO.getRow(),
                                moduleCacheMap
                                        .get(currentModuleId)
                                        .getModuleInfo()
                                        .getPrimaryTable());
                if (dataId != null && dataId >= 0) {
                    updateDataIds.add(dataId);
                }
            }
        }
        // 获取父数据 这个模块下 详情页中 所有的ID
        Set<Long> dbIds =
                parentToChildDataMap
                        .getOrDefault(parentModuleId, new HashMap<>())
                        .getOrDefault(parentDataId, new HashSet<>());
        // 找出 需要删除的 ID
        Set<Long> deleteIds =
                dbIds.stream()
                        .filter(x -> x != null && !updateDataIds.contains(x))
                        .collect(Collectors.toSet());

        // 有删除的数据 要有写字段权限
        if (deleteIds.size() > 0) {
            // 这个模块 下 有数据要删除，需要有写字段权限
            Boolean canWrite =
                    sysModuleCombineInfo
                                    .getWriteFields()
                                    .getOrDefault(
                                            sysModuleCombineInfo.getModuleInfo().getPrimaryTable(),
                                            new HashSet<>())
                                    .size()
                            > 0;
            if (!canWrite) {
                result.computeIfAbsent(
                                sysModuleCombineInfo.getModuleInfo().getModuleId(),
                                k -> new HashMap<>())
                        .computeIfAbsent("apply", k -> new HashSet<>())
                        .add(sysModuleCombineInfo.getModuleInfo().getPrimaryTable() + ".deleted");
            }
        }

        if (CollectionUtil.isNotEmpty(param.getRecord())) {
            for (RecordDataDTO recordDataDTO : param.getRecord()) {
                Long dataId =
                        DataPersistenceUtil.getTableRowIdWithTabName(
                                recordDataDTO.getRow(),
                                moduleCacheMap
                                        .get(currentModuleId)
                                        .getModuleInfo()
                                        .getPrimaryTable());
                boolean isInsert = isOverAllInsert || (dataId == null || dataId <= 0);
                checkRow(recordDataDTO, dataMap, sysModuleCombineInfo, isOverAllInsert, result);
                if (CollectionUtil.isNotEmpty(recordDataDTO.getChildren())) {
                    for (NestedModuleDataDTO child : recordDataDTO.getChildren()) {
                        checkNestedDataPermission(
                                child,
                                dataMap,
                                parentToChildDataMap,
                                currentModuleId,
                                dataId,
                                isInsert,
                                moduleCacheMap,
                                result);
                    }
                }
            }
        }
    }

    // 返回这行是新增 还是修改
    private boolean checkRow(
            RecordDataDTO recordDataDTO,
            Map<Long, Map<Long, Map<String, Object>>> dataMap,
            SysModuleCombineInfo sysModuleCombineInfo,
            Boolean isInsert,
            Map<Long, Map<String, Set<String>>> result) {
        if (CollectionUtil.isNotEmpty(recordDataDTO.getRow())) {
            Set<String> uncheckedFields = new HashSet<>();
            Set<String> tabs = new HashSet<>();
            tabs.add(sysModuleCombineInfo.getModuleInfo().getPrimaryTable());
            if (CollectionUtil.isNotEmpty(sysModuleCombineInfo.getSubTables())) {
                tabs.addAll(sysModuleCombineInfo.getSubTables());
            }
            for (String table : tabs) {
                // 模块所有表的 忽略的字段
                for (String f : UNCHECK_PERMISSION_FIELDS) {
                    uncheckedFields.add(table + "." + f);
                }
                // 模块所有表 关联上级表的字段 忽略
                if (sysModuleCombineInfo.getSubTableRelationMap().get(table) != null) {
                    uncheckedFields.add(
                            table
                                    + "."
                                    + sysModuleCombineInfo
                                            .getSubTableRelationMap()
                                            .get(table)
                                            .getJoinField());
                }
            }
            Long dataId =
                    DataPersistenceUtil.getTableRowIdWithTabName(
                            recordDataDTO.getRow(),
                            sysModuleCombineInfo.getModuleInfo().getPrimaryTable());
            // 新增的行数据
            if (isInsert || (dataId == null || dataId <= 0)) {
                // 插入
                for (String key : recordDataDTO.getRow().keySet()) {
                    if (uncheckedFields.contains(key)) {
                        continue;
                    }
                    String[] field = key.split("\\.");
                    boolean canWrite =
                            sysModuleCombineInfo
                                    .getWriteFields()
                                    .getOrDefault(field[0], new HashSet<>())
                                    .contains(field[1]);
                    if (!canWrite) {
                        // 没有写权限字段
                        result.computeIfAbsent(
                                        sysModuleCombineInfo.getModuleInfo().getModuleId(),
                                        k -> new HashMap<>())
                                .computeIfAbsent("apply", k -> new HashSet<>())
                                .add(key);
                    }
                }
                return true;
            } else {
                // 修改的行数据
                for (String key : recordDataDTO.getRow().keySet()) {
                    if (uncheckedFields.contains(key)) {
                        continue;
                    }
                    Object newValue = recordDataDTO.getRow().get(key);
                    Object dbValue =
                            dataMap.get(sysModuleCombineInfo.getModuleInfo().getModuleId())
                                    .get(dataId)
                                    .get(key);

                    // 没有修改
                    boolean isEqual = DataPersistenceUtil.isEqualValue(newValue, dbValue);
                    if (isEqual) {
                        continue;
                    }
                    // 修改了
                    String[] field = key.split(".");
                    boolean canUpdate =
                            sysModuleCombineInfo
                                    .getUpdateFields()
                                    .getOrDefault(field[0], new HashSet<>())
                                    .contains(field[1]);
                    if (!canUpdate) {
                        //
                        // 没有编辑 权限字段
                        result.computeIfAbsent(
                                        sysModuleCombineInfo.getModuleInfo().getModuleId(),
                                        k -> new HashMap<>())
                                .computeIfAbsent("edit", k -> new HashSet<>())
                                .add(key);
                    }
                }
                return false;
            }
        }
        return true;
    }
}
