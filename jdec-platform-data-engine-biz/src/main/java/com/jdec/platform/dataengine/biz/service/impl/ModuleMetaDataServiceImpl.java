package com.jdec.platform.dataengine.biz.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.config.api.SysApprovalChainConfigApi;
import com.jdec.platform.config.api.SysFieldApi;
import com.jdec.platform.config.api.SysModuleFieldPermissionApi;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.response.PermissionTableGroupResp;
import com.jdec.platform.config.api.dto.response.SysFieldResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.dto.context.ModuleQueryContext;
import com.jdec.platform.dataengine.biz.service.ModuleMetaDataService;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class ModuleMetaDataServiceImpl implements ModuleMetaDataService {

    private final ConfigApi configApi;

    private final SysModuleFieldPermissionApi sysModuleFieldPermissionApi;

    private final SysFieldApi sysFieldApi;

    private final SysApprovalChainConfigApi sysApprovalChainConfigApi;

    @Override
    public Map<Long, SysModuleCombineInfo> getModuleMetaDataMap(
            ModuleQueryContext moduleQueryContext) {
        Map<Long, SysModuleCombineInfo> moduleCacheMap = new HashMap<>();

        ModuleTreeDTO moduleTreeDTO =
                configApi.getModuleTree(
                        moduleQueryContext.getProjectNo(),
                        moduleQueryContext.getSubjectId(),
                        moduleQueryContext.getModuleId());
        while (moduleTreeDTO.getRootModule().getParentId() != null
                && !Long.valueOf(0L).equals(moduleTreeDTO.getRootModule().getParentId())) {
            moduleTreeDTO =
                    configApi.getModuleTree(
                            moduleQueryContext.getProjectNo(),
                            moduleQueryContext.getSubjectId(),
                            moduleTreeDTO.getRootModule().getParentId());
        }
        ProjectTableRelationsDTO projectTableRelationsDTO =
                configApi.getProjectTableRelations(moduleQueryContext.getProjectNo());

        for (Map.Entry<Long, ModuleInfo> entry : moduleTreeDTO.getModuleMap().entrySet()) {
            SysModuleCombineInfo moduleCombineInfo = new SysModuleCombineInfo();
            moduleCombineInfo.setModuleTree(moduleTreeDTO);

            ModuleInfo moduleInfo = entry.getValue();
            moduleCombineInfo.setModuleInfo(moduleInfo);
            moduleCombineInfo.setProjectTableRelations(projectTableRelationsDTO);

            if (CollectionUtil.isNotEmpty(moduleInfo.getFields())) {
                Set<String> subTables =
                        moduleInfo.getFields().stream()
                                .filter(
                                        x ->
                                                !StringUtils.equals(
                                                        x.getTableName(),
                                                        moduleInfo.getPrimaryTable()))
                                .map(x -> x.getTableName())
                                .collect(Collectors.toSet());
                moduleCombineInfo.setSubTables(subTables);
                moduleCombineInfo.getAllTables().add(moduleInfo.getPrimaryTable());
                if (CollectionUtil.isNotEmpty(subTables)) {
                    moduleCombineInfo.getAllTables().addAll(subTables);
                }
                moduleInfo.getFields().stream()
                        .forEach(
                                x -> {
                                    moduleCombineInfo
                                            .getFieldMapByTable()
                                            .computeIfAbsent(x.getTableName(), k -> new HashMap<>())
                                            .put(x.getColumnName(), x);
                                });
                moduleInfo.getFields().stream()
                        .forEach(
                                x ->
                                        moduleCombineInfo
                                                .getFieldAllMap()
                                                .put(
                                                        x.getTableName() + "." + x.getColumnName(),
                                                        x));

                // 配置唯一名称字段
                List<String> bizKeyColumns =
                        moduleInfo.getFields().stream()
                                .filter(x -> x.getBizKeyOrder() != null)
                                .sorted(Comparator.comparingInt(ModuleFieldDTO::getBizKeyOrder))
                                .map(x -> x.getColumnName())
                                .collect(Collectors.toList());
                if (CollectionUtil.isNotEmpty(bizKeyColumns)) {
                    moduleCombineInfo.setUniqueNameFields(bizKeyColumns);
                }
            }
            // 构建主表关联 关系
            moduleCombineInfo.setMainTableRelationDTO(
                    projectTableRelationsDTO.getAllRelations().stream()
                            .filter(
                                    x ->
                                            StringUtils.equals(
                                                    x.getJoinTable(), moduleInfo.getPrimaryTable()))
                            .findFirst()
                            .orElse(null));
            if (moduleCombineInfo.getMainTableRelationDTO() != null) {
                moduleCombineInfo
                        .getSubTableRelationMap()
                        .put(
                                moduleInfo.getPrimaryTable(),
                                moduleCombineInfo.getMainTableRelationDTO());
            }
            if (CollectionUtil.isNotEmpty(moduleCombineInfo.getSubTables())) {
                for (String subTable : moduleCombineInfo.getSubTables()) {
                    moduleCombineInfo
                            .getSubTableRelationMap()
                            .put(
                                    subTable,
                                    projectTableRelationsDTO.getAllRelations().stream()
                                            .filter(
                                                    x ->
                                                            StringUtils.equals(
                                                                    x.getJoinTable(), subTable))
                                            .findFirst()
                                            .orElse(null));
                }
            }

            // 构建模块 字段权限数据
            if (moduleQueryContext.getIsBuildFieldPermission() != null
                    && moduleQueryContext.getIsBuildFieldPermission()) {
                List<PermissionTableGroupResp> permissionTableGroupResps =
                        sysModuleFieldPermissionApi.getRoleModuleFieldPermissions(
                                moduleQueryContext.getProjectNo(),
                                moduleQueryContext.getSubjectId(),
                                moduleQueryContext.getRoleId(),
                                entry.getKey());
                if (CollectionUtil.isNotEmpty(permissionTableGroupResps)) {
                    for (PermissionTableGroupResp resp : permissionTableGroupResps) {
                        if (CollectionUtil.isNotEmpty(resp.getWritableFields())) {
                            resp.getWritableFields().stream()
                                    .forEach(
                                            x -> {
                                                moduleCombineInfo
                                                        .getWriteFields()
                                                        .computeIfAbsent(
                                                                x.getTableName(),
                                                                k -> new HashSet<>())
                                                        .add(x.getColumnName());
                                            });
                        }
                        if (CollectionUtil.isNotEmpty(resp.getUpdatableFields())) {
                            resp.getUpdatableFields().stream()
                                    .forEach(
                                            x -> {
                                                moduleCombineInfo
                                                        .getUpdateFields()
                                                        .computeIfAbsent(
                                                                x.getTableName(),
                                                                k -> new HashSet<>())
                                                        .add(x.getColumnName());
                                            });
                        }
                        if (CollectionUtil.isNotEmpty(resp.getReadableFields())) {
                            resp.getReadableFields().stream()
                                    .forEach(
                                            x -> {
                                                moduleCombineInfo
                                                        .getReadFields()
                                                        .computeIfAbsent(
                                                                x.getTableName(),
                                                                k -> new HashSet<>())
                                                        .add(x.getColumnName());
                                            });
                        }
                    }
                }
            }

            // 构建模块 字段属性
            if (moduleQueryContext.getIsBuildFieldPro() != null
                    && moduleQueryContext.getIsBuildFieldPro()) {
                Set<String> tables =
                        moduleInfo.getFields().stream()
                                .map(x -> x.getTableName())
                                .collect(Collectors.toSet());
                for (String t : tables) {
                    List<SysFieldResp> columnInfoRespList =
                            sysFieldApi.querySimpleSysFields(
                                    moduleQueryContext.getProjectNo(),
                                    moduleQueryContext.getSubjectId(),
                                    t,
                                    null);
                    if (CollectionUtil.isNotEmpty(columnInfoRespList)) {
                        columnInfoRespList.stream()
                                .forEach(
                                        x -> {
                                            moduleCombineInfo
                                                    .getAllFieldsByTable()
                                                    .computeIfAbsent(
                                                            x.getTableName(), k -> new HashMap<>())
                                                    .put(x.getColumnName(), x);
                                        });
                        columnInfoRespList.stream()
                                .forEach(
                                        x -> {
                                            moduleCombineInfo
                                                    .getAllFields()
                                                    .put(
                                                            x.getTableName()
                                                                    + "."
                                                                    + x.getColumnName(),
                                                            x);
                                        });
                    }
                }
            }
            moduleCacheMap.put(entry.getKey(), moduleCombineInfo);
        }
        return moduleCacheMap;
    }

    @Override
    public Map<Long, SysModuleCombineInfo> getModuleMetaDataMapAsync(
            ModuleQueryContext moduleQueryContext) {
        CompletableFuture<Map<Long, SysModuleCombineInfo>> moduleMapCacheFuture =
                CompletableFuture.supplyAsync(
                        () -> {
                            return getModuleMetaDataMap(moduleQueryContext);
                        });
        try {
            return moduleMapCacheFuture.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Boolean getApprovalStatusByModuleId(Long moduleId) {
        return sysApprovalChainConfigApi.getApprovalStatus(List.of(moduleId));
    }

    @Override
    public Boolean getApprovalStatusByModuleIdAsync(Long moduleId) {
        CompletableFuture<Boolean> future =
                CompletableFuture.supplyAsync(
                        () -> {
                            return getApprovalStatusByModuleId(moduleId);
                        });
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
