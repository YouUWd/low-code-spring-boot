package com.jdec.platform.dataengine.biz.service;

import com.jdec.platform.dataengine.api.DataEngineApi;
import com.jdec.platform.dataengine.api.dto.model.ModuleFieldDTO;
import com.jdec.platform.dataengine.api.dto.model.ModuleNodeDTO;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.SysModuleInfo;
import com.jdec.platform.dataengine.api.dto.request.ApprovalProcessReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicQueryReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.api.dto.response.ApprovalStepInfo;
import com.jdec.platform.dataengine.api.dto.response.DetailDataResp;
import com.jdec.platform.dataengine.api.dto.response.DynamicQueryResp;
import com.jdec.platform.dataengine.api.dto.response.VersionCompareInfo;
import com.jdec.platform.dataengine.biz.config.service.DataModuleConfigService;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataPersistenceService;
import com.jdec.platform.shared.context.AppContext;
import java.util.ArrayList;
import java.util.List;
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

    private final DataModuleConfigService dataModuleConfigService;

    @Override
    public DynamicQueryResp query(DynamicQueryReq req) {
        return dynamicQueryService.query(req);
    }

    @Override
    public DetailDataResp<NestedModuleDataDTO> getDetail(
            Long rootModuleId, Long id, String btnType, List<Long> filterFields) {

        SysModuleInfo sysModuleInfo =
                dataModuleConfigService.getModuleCompleteById(
                        AppContext.getProjectNo(), AppContext.getSubjectId(), rootModuleId);

        SysModuleInfo meta = copyAndFilterModuleTree(sysModuleInfo, filterFields);

        List<NestedModuleDataDTO> detailDatas =
                dynamicQueryService.getDetail(rootModuleId, id, btnType, filterFields);

        return DetailDataResp.of(
                meta, CollectionUtils.isEmpty(detailDatas) ? null : detailDatas.get(0));
    }

    @Override
    public Long save(DynamicSaveReq req) {
        return moduleDataPersistenceService.save(req);
    }

    @Override
    public ApprovalStepInfo getApprovalStepInfo(ApprovalProcessReq approvalProcessReq) {
        return null;
    }

    @Override
    public VersionCompareInfo getVersionCompareInfo(Long primaryKeyId) {
        return null;
    }

    // ====================== 可选：深拷贝版本（不修改原始树，返回新树，推荐业务使用） ======================
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

        // 拷贝并过滤字段
        List<ModuleFieldDTO> filteredFields =
                sourceNode.getFields().stream()
                        .filter(
                                f ->
                                        CollectionUtils.isEmpty(allowedFieldCodeSet)
                                                || allowedFieldCodeSet.contains(f.getId()))
                        .map(
                                srcField -> {
                                    ModuleFieldDTO newField = new ModuleFieldDTO();
                                    BeanUtils.copyProperties(srcField, newField);
                                    return newField;
                                })
                        .collect(Collectors.toList());
        targetNode.setFields(filteredFields);

        // 递归拷贝子节点
        List<SysModuleInfo> newChildren = new ArrayList<>();
        List<ModuleNodeDTO> directChildNodes = sourceNode.getModuleNodes();
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
        }
        return targetNode;
    }
}
