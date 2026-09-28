package com.jdec.platform.dataengine.biz.service;

import com.jdec.platform.config.api.SysModuleFieldPermissionApi;
import com.jdec.platform.config.api.dto.response.PermissionTableGroupResp;
import com.jdec.platform.dataengine.api.DataEngineApi;
import com.jdec.platform.dataengine.api.dto.model.ModuleFieldDTO;
import com.jdec.platform.dataengine.api.dto.model.ModuleNodeDTO;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.SysModuleInfo;
import com.jdec.platform.dataengine.api.dto.request.*;
import com.jdec.platform.dataengine.api.dto.response.ApprovalProcessResultResp;
import com.jdec.platform.dataengine.api.dto.response.DetailDataResp;
import com.jdec.platform.dataengine.api.dto.response.DynamicQueryResp;
import com.jdec.platform.dataengine.api.dto.response.VersionListResp;
import com.jdec.platform.dataengine.biz.config.service.DataModuleConfigService;
import com.jdec.platform.dataengine.biz.dto.FieldPermissionItem;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataApprovalBizService;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataPersistenceService;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
public class DataEngineService implements DataEngineApi {

    private final DynamicQueryService dynamicQueryService;
    private final ModuleDataPersistenceService moduleDataPersistenceService;
    private final ModuleDataApprovalBizService moduleDataApprovalBizService;

    private final DataModuleConfigService dataModuleConfigService;

    private final SysModuleFieldPermissionApi sysModuleFieldPermissionApi;

    @Override
    public DynamicQueryResp query(DynamicQueryReq req) {
        return dynamicQueryService.query(req);
    }

    @Override
    public DetailDataResp<NestedModuleDataDTO> getDetail(
            DynamicDetailQueryReq dynamicDetailQueryReq) {

        SysModuleInfo sysModuleInfo =
                dataModuleConfigService.getModuleCompleteById(
                        AppContext.getProjectNo(),
                        AppContext.getSubjectId(),
                        dynamicDetailQueryReq.getModuleId());

        SysModuleInfo meta = copyAndFilterModuleTree(sysModuleInfo, List.of());

        List<SysModuleInfo> metaList = getListStructure(meta);

        List<NestedModuleDataDTO> detailDatas =
                dynamicQueryService.getDetail(
                        dynamicDetailQueryReq, dynamicDetailQueryReq.getBtnType());

        return DetailDataResp.of(
                metaList, CollectionUtils.isEmpty(detailDatas) ? null : detailDatas.get(0));
    }

    private List<SysModuleInfo> getListStructure(SysModuleInfo meta) {
        List<SysModuleInfo> list = new ArrayList<>();
        list.add(meta);
        if (!CollectionUtils.isEmpty(meta.getChildren())) {
            for (SysModuleInfo sysModuleInfo : meta.getChildren()) {
                list.addAll(getListStructure(sysModuleInfo));
            }
        }
        return list;
    }

    @Override
    public Long save(DynamicSaveReq req) {
        return moduleDataPersistenceService.save(req);
    }

    @Override
    public ApprovalProcessResultResp processApprovalStatus(ApprovalStatusCallBackReq req) {
        return moduleDataApprovalBizService.processApprovalStatus(req);
    }

    @Override
    public List<VersionListResp> getVersionList(VersionListQueryReq versionListQueryReq) {
        return dynamicQueryService.getVersionList(versionListQueryReq);
    }

    // ======================  深拷贝版本（不修改原始树，返回新树，推荐业务使用） ======================
    /** 深拷贝+字段过滤，不会改动原始模块树对象 */
    public SysModuleInfo copyAndFilterModuleTree(
            SysModuleInfo sourceRoot, List<Long> allowedFieldCodeSet) {
        if (sourceRoot == null) {
            return sourceRoot;
        }
        return recursiveCopyAndFilter(sourceRoot, allowedFieldCodeSet);
    }

    private SysModuleInfo recursiveCopyAndFilter(
            SysModuleInfo sourceNode, List<Long> allowedFieldCodeSet) {
        SysModuleInfo targetNode = new SysModuleInfo();
        targetNode.setModuleCode(sourceNode.getModuleCode());
        targetNode.setModuleName(sourceNode.getModuleName());
        targetNode.setParentId(sourceNode.getParentId());
        targetNode.setModuleDesc(sourceNode.getModuleDesc());
        targetNode.setModuleId(sourceNode.getModuleId());
        targetNode.setPrimaryTable(sourceNode.getPrimaryTable());

        List<PermissionTableGroupResp> moduleFieldsPermission =
                sysModuleFieldPermissionApi.getRoleModuleFieldPermissions(
                        AppContext.getProjectNo(),
                        AppContext.getSubjectId(),
                        UserContext.getRoleId(),
                        sourceNode.getModuleId());

        Map<String, FieldPermissionItem> fieldPermissionMap = new HashMap<>();

        for (PermissionTableGroupResp permissionTableGroupResp : moduleFieldsPermission) {

            permissionTableGroupResp
                    .getReadableFields()
                    .forEach(
                            ele -> {
                                fieldPermissionMap
                                        .computeIfAbsent(
                                                permissionTableGroupResp.getTableName()
                                                        + "."
                                                        + ele.getColumnName(),
                                                v -> FieldPermissionItem.builder().build())
                                        .setView(true);
                            });

            permissionTableGroupResp
                    .getUpdatableFields()
                    .forEach(
                            ele -> {
                                fieldPermissionMap
                                        .computeIfAbsent(
                                                permissionTableGroupResp.getTableName()
                                                        + "."
                                                        + ele.getColumnName(),
                                                v -> FieldPermissionItem.builder().build())
                                        .setEdit(true);
                            });

            permissionTableGroupResp
                    .getWritableFields()
                    .forEach(
                            ele -> {
                                fieldPermissionMap
                                        .computeIfAbsent(
                                                permissionTableGroupResp.getTableName()
                                                        + "."
                                                        + ele.getColumnName(),
                                                v -> FieldPermissionItem.builder().build())
                                        .setApply(true);
                            });
        }

        // 拷贝并过滤字段
        List<ModuleFieldDTO> filteredFields =
                sourceNode.getFieldList().stream()
                        .filter(
                                f ->
                                        CollectionUtils.isEmpty(allowedFieldCodeSet)
                                                || allowedFieldCodeSet.contains(f.getId()))
                        .map(
                                srcField -> {
                                    ModuleFieldDTO newField = new ModuleFieldDTO();
                                    BeanUtils.copyProperties(srcField, newField);
                                    newField.setView(false);
                                    newField.setEdit(false);
                                    newField.setApply(false);
                                    if (fieldPermissionMap.containsKey(
                                            newField.getTableName()
                                                    + "."
                                                    + newField.getColumnName())) {
                                        newField.setApply(
                                                fieldPermissionMap
                                                                        .get(
                                                                                newField
                                                                                                .getTableName()
                                                                                        + "."
                                                                                        + newField
                                                                                                .getColumnName())
                                                                        .getApply()
                                                                != null
                                                        && fieldPermissionMap
                                                                .get(
                                                                        newField.getTableName()
                                                                                + "."
                                                                                + newField
                                                                                        .getColumnName())
                                                                .getApply());
                                        newField.setEdit(
                                                fieldPermissionMap
                                                                        .get(
                                                                                newField
                                                                                                .getTableName()
                                                                                        + "."
                                                                                        + newField
                                                                                                .getColumnName())
                                                                        .getEdit()
                                                                != null
                                                        && fieldPermissionMap
                                                                .get(
                                                                        newField.getTableName()
                                                                                + "."
                                                                                + newField
                                                                                        .getColumnName())
                                                                .getEdit());
                                        newField.setView(
                                                fieldPermissionMap
                                                                        .get(
                                                                                newField
                                                                                                .getTableName()
                                                                                        + "."
                                                                                        + newField
                                                                                                .getColumnName())
                                                                        .getView()
                                                                != null
                                                        && fieldPermissionMap
                                                                .get(
                                                                        newField.getTableName()
                                                                                + "."
                                                                                + newField
                                                                                        .getColumnName())
                                                                .getView());
                                    }
                                    return newField;
                                })
                        .collect(Collectors.toList());
        targetNode.setFieldList(filteredFields);

        Map<String, ModuleFieldDTO> fieldMap =
                filteredFields.stream()
                        .collect(
                                Collectors.toMap(
                                        f -> f.getTableName() + "." + f.getColumnName(), f -> f));
        targetNode.setFileds(fieldMap);

        // 递归拷贝子节点
        List<SysModuleInfo> newChildren = new ArrayList<>();
        List<ModuleNodeDTO> directChildNodes =
                sourceNode.getModuleNodes().stream()
                        .filter(ele -> ele.getParentId().equals(sourceNode.getModuleId()))
                        .collect(Collectors.toList());
        if (directChildNodes != null && !directChildNodes.isEmpty()) {
            directChildNodes.forEach(
                    child -> {
                        if (child != null && child.getId() != null) {
                            SysModuleInfo childMeta =
                                    dataModuleConfigService.getModuleCompleteById(
                                            AppContext.getProjectNo(),
                                            AppContext.getSubjectId(),
                                            child.getId());
                            if (childMeta != null) {
                                SysModuleInfo assembledChild =
                                        recursiveCopyAndFilter(childMeta, allowedFieldCodeSet);
                                newChildren.add(assembledChild);
                            }
                        }
                    });
            targetNode.setChildren(newChildren);
            targetNode.setModuleNodes(directChildNodes);
        }
        return targetNode;
    }
}
