package com.jdec.platform.dataengine.biz.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.config.api.SysFieldApi;
import com.jdec.platform.config.api.SysModuleFieldPermissionApi;
import com.jdec.platform.config.api.dto.response.ColumnInfoResp;
import com.jdec.platform.config.api.dto.response.PermissionTableGroupResp;
import com.jdec.platform.dataengine.api.dto.model.SysModuleInfo;
import com.jdec.platform.dataengine.biz.config.service.DataModuleConfigService;
import com.jdec.platform.dataengine.biz.dto.context.ModuleQueryContext;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.service.ModuleMetaDataService;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class ModuleMetaDataServiceImpl implements ModuleMetaDataService {

    private final DataModuleConfigService dataModuleConfigService;

    private final SysModuleFieldPermissionApi sysModuleFieldPermissionApi;

    private final SysFieldApi sysFieldApi;

    @Override
    public Map<Long, SysModuleInfo> getModuleMetaDataMap(ModuleQueryContext moduleQueryContext) {
        Map<Long, SysModuleInfo> moduleCacheMap = new HashMap<>();
        Set<Long> moduleNodeIds = new HashSet<>();
        moduleNodeIds.add(moduleQueryContext.getModuleId());
        while (!moduleNodeIds.isEmpty()) {
            Set<Long> nextModuleNodeIds = new HashSet<>();

            for (Long moduleNodeId : moduleNodeIds) {
                SysModuleInfo moduleNode =
                        dataModuleConfigService.getModuleCompleteById(
                                moduleQueryContext.getProjectNo(),
                                moduleQueryContext.getSubjectId(),
                                moduleNodeId);
                if (moduleNode != null) {
                    // 下级子模块
                    if (CollectionUtil.isNotEmpty(moduleNode.getModuleNodes())) {
                        nextModuleNodeIds.addAll(
                                moduleNode.getModuleNodes().stream()
                                        .map(x -> x.getId())
                                        .collect(Collectors.toSet()));
                    }

                    if (CollectionUtil.isNotEmpty(moduleNode.getFieldList())) {
                        Set<String> subTables =
                                moduleNode.getFieldList().stream()
                                        .filter(
                                                x ->
                                                        moduleNode
                                                                .getPrimaryTable()
                                                                .equals(x.getTableName()))
                                        .map(x -> x.getTableName())
                                        .collect(Collectors.toSet());
                        moduleNode.setSubTables(subTables);
                    }

                    // 构建模块 字段权限数据
                    if (moduleQueryContext.getIsPermission() != null
                            && moduleQueryContext.getIsPermission()) {
                        List<PermissionTableGroupResp> permissionTableGroupResps =
                                sysModuleFieldPermissionApi.getRoleModuleFieldPermissions(
                                        moduleQueryContext.getProjectNo(),
                                        moduleQueryContext.getSubjectId(),
                                        moduleQueryContext.getRoleId(),
                                        moduleNode.getModuleId());
                        Set<String> writeFields = new HashSet<>();
                        Set<String> updateFields = new HashSet<>();
                        Set<String> readFields = new HashSet<>();
                        if (CollectionUtil.isNotEmpty(permissionTableGroupResps)) {
                            for (PermissionTableGroupResp resp : permissionTableGroupResps) {
                                if (CollectionUtil.isNotEmpty(resp.getWritableFields())) {
                                    writeFields.addAll(
                                            resp.getWritableFields().stream()
                                                    .map(
                                                            x ->
                                                                    x.getTableName()
                                                                            + "."
                                                                            + x.getColumnName())
                                                    .collect(Collectors.toSet()));
                                }
                                if (CollectionUtil.isNotEmpty(resp.getUpdatableFields())) {
                                    updateFields.addAll(
                                            resp.getUpdatableFields().stream()
                                                    .map(
                                                            x ->
                                                                    x.getTableName()
                                                                            + "."
                                                                            + x.getColumnName())
                                                    .collect(Collectors.toSet()));
                                }
                                if (CollectionUtil.isNotEmpty(resp.getReadableFields())) {
                                    readFields.addAll(
                                            resp.getReadableFields().stream()
                                                    .map(
                                                            x ->
                                                                    x.getTableName()
                                                                            + "."
                                                                            + x.getColumnName())
                                                    .collect(Collectors.toSet()));
                                }
                            }
                            moduleNode.setWriteFields(writeFields);
                            moduleNode.setUpdateFields(updateFields);
                            moduleNode.setReadFields(readFields);
                        }
                    }
                    if (moduleQueryContext.getIsField() != null
                            && moduleQueryContext.getIsField()) {
                        // 构建模块 表字段
                        Set<String> tables =
                                moduleNode.getFieldList().stream()
                                        .map(x -> x.getTableName())
                                        .collect(Collectors.toSet());
                        Set<String> allFields = new HashSet<>();
                        for (String t : tables) {
                            List<ColumnInfoResp> columnInfoRespList =
                                    sysFieldApi.getTableColumns(
                                            AppContext.getProjectNo(),
                                            AppContext.getSubjectId(),
                                            t);
                            if (CollectionUtil.isNotEmpty(columnInfoRespList)) {
                                allFields.addAll(
                                        columnInfoRespList.stream()
                                                .map(x -> t + "." + x.getColumnName())
                                                .collect(Collectors.toSet()));
                            }
                        }
                        moduleNode.setAllFields(allFields);
                    }
                    moduleCacheMap.put(moduleNode.getModuleId(), moduleNode);
                }
            }
            moduleNodeIds = nextModuleNodeIds;
        }

        SysModuleInfo rootModule = moduleCacheMap.get(moduleQueryContext.getModuleId());
        if (rootModule == null) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        }
        return moduleCacheMap;
    }
}
