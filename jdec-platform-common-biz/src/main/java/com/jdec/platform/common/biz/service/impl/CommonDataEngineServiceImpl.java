package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.common.api.UserMenuHeaderApi;
import com.jdec.platform.common.api.dto.response.FieldMetaDTO;
import com.jdec.platform.common.api.dto.response.ModuleMetaDTO;
import com.jdec.platform.common.api.dto.response.ModuleSimplePermissionDTO;
import com.jdec.platform.common.biz.dto.request.MenuDetailQueryReq;
import com.jdec.platform.common.biz.dto.request.MenuVersionListQueryReq;
import com.jdec.platform.common.biz.dto.response.DetailDataResp;
import com.jdec.platform.common.biz.service.CommonDataEngineService;
import com.jdec.platform.config.api.SysApprovalChainConfigApi;
import com.jdec.platform.config.api.SysMenuApi;
import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.dto.common.*;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
import com.jdec.platform.config.api.dto.response.SysMenuResp;
import com.jdec.platform.config.api.dto.response.SysModuleSimpleTreeResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.dataengine.api.DataEngineApi;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.api.dto.request.DynamicDetailQueryReq;
import com.jdec.platform.dataengine.api.dto.request.VersionListQueryReq;
import com.jdec.platform.dataengine.api.dto.response.CreatedUserInfoResp;
import com.jdec.platform.dataengine.api.dto.response.VersionListResp;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/** 根据菜单查询详情数据 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommonDataEngineServiceImpl implements CommonDataEngineService {

    private final DataEngineApi dataEngineApi;

    private final ConfigApi configApi;

    private final SysMenuApi sysMenuApi;

    private final SysModuleApi sysModuleApi;

    private final UserMenuHeaderApi userMenuHeaderApi;

    private final SysApprovalChainConfigApi sysApprovalChainConfigApi;

    private final IApprovalEngine iApprovalEngine;

    @Override
    public DetailDataResp<NestedModuleDataDTO> getDetailByMenu(
            MenuDetailQueryReq menuDetailQueryReq) {

        if (menuDetailQueryReq.getMenuIds() == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }
        if (menuDetailQueryReq.getId() == null) {
            throw new IllegalArgumentException("单据 ID 不能为空");
        }

        Long userId = UserContext.getUserId();
        Long roleId = UserContext.getRoleId();

        List<SysMenuResp> menuList = sysMenuApi.list(1);

        if (CollectionUtils.isEmpty(menuList)) {
            return null;
        }

        List<SysMenuResp> targetMenus =
                menuList.stream()
                        .filter(ele -> menuDetailQueryReq.getMenuIds().contains(ele.getId()))
                        .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(targetMenus)) {
            return null;
        }

        // 菜单对应的模块集合
        Set<Long> moduleIds =
                targetMenus.stream().map(SysMenuResp::getModuleId).collect(Collectors.toSet());

        List<SysModuleSimpleTreeResp> moduleTree =
                sysModuleApi.getAvailableModuleTree(
                        1, AppContext.getProjectNo(), AppContext.getSubjectId());

        // 模块树上寻找根节点
        SysModuleSimpleTreeResp rootModule = findRootNodesByTargetIds(moduleTree, moduleIds);

        if (Objects.isNull(rootModule)) {
            throw new BusinessException("菜单 [" + menuDetailQueryReq.getMenuIds() + "] 没有共同的父模块");
        }

        ModuleTreeDTO rootNode =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModule.getId());

        // 字段权限映射Map获取
        Map<Long, ModulePermissionDTO> permMap =
                configApi.getModulePermissions(
                        roleId, new ArrayList<>(rootNode.getAllowedModuleIds()));

        Map<Long, MenuHeaderColumnResp> moduleIdHeaderMap = new HashMap<>();

        List<Long> checkedFieldIds = new ArrayList<>();

        Boolean hasApproval =
                sysApprovalChainConfigApi.getApprovalStatus(List.of(rootModule.getId()));

        // 设置待查看模块表头及模块可查看字段
        for (SysMenuResp targetMenu : targetMenus) {

            List<Long> fieldIds = new ArrayList<>();

            MenuHeaderDisplayResp menuHeaderDisplayResp =
                    userMenuHeaderApi.getUserMenuHeaderDisplay(
                            UserContext.getUserId(),
                            targetMenu.getId(),
                            UserContext.getRoleId(),
                            "detail",
                            menuDetailQueryReq.getId() > 0 ? "edit" : "apply",
                            Objects.nonNull(hasApproval) ? (hasApproval ? 1 : 0) : 0,
                            roleId);

            if (Objects.nonNull(menuHeaderDisplayResp)) {
                moduleIdHeaderMap.put(targetMenu.getModuleId(), menuHeaderDisplayResp.getHeader());
            }

            if (Objects.isNull(rootNode)) {
                log.debug("模块树查询结果未空，返回空列表。");
            }

            // 对模块下字段进行权限过滤
            if (permMap.containsKey(targetMenu.getModuleId())) {
                Set<Long> modulePermissionFields =
                        permMap
                                .get(targetMenu.getModuleId())
                                .getFieldPermissions()
                                .entrySet()
                                .stream()
                                .filter(
                                        ele -> {
                                            ModulePermissionDTO.FieldPermissionItem
                                                    fieldPermissionItem = ele.getValue();
                                            // 返回有权限的字段
                                            return fieldPermissionItem.getEdit()
                                                    || fieldPermissionItem.getView();
                                        })
                                .map(item -> item.getKey())
                                .collect(Collectors.toSet());
                fieldIds.addAll(
                        rootNode.getModule(targetMenu.getModuleId()).getFields().stream()
                                .filter(ele -> modulePermissionFields.contains(ele.getId()))
                                .map(ModuleFieldDTO::getId)
                                .collect(Collectors.toSet()));
            }

            checkedFieldIds.addAll(fieldIds);
        }

        // 获取根模块的business_status字段，手动加入到查询列表中
        Map.Entry<Long, ModuleFieldDTO> businessStatusField =
                rootNode.getFieldMap().entrySet().stream()
                        .filter(
                                ele ->
                                        ele.getValue().getModuleId().equals(rootModule.getId())
                                                && ele.getValue()
                                                        .getColumnName()
                                                        .equals("business_status"))
                        .findFirst()
                        .orElse(null);

        Map.Entry<Long, ModuleFieldDTO> createBy =
                rootNode.getFieldMap().entrySet().stream()
                        .filter(
                                ele ->
                                        ele.getValue().getModuleId().equals(rootModule.getId())
                                                && ele.getValue()
                                                        .getColumnName()
                                                        .equals("created_by"))
                        .findFirst()
                        .orElse(null);

        if (Objects.nonNull(businessStatusField)) {
            checkedFieldIds.add(businessStatusField.getKey());
        }

        if (Objects.nonNull(createBy)) {
            checkedFieldIds.add(createBy.getKey());
        }

        // 根模块加入
        moduleIds.add(rootModule.getId());

        DynamicDetailQueryReq dynamicDetailQueryReq =
                DynamicDetailQueryReq.builder()
                        .moduleId(rootModule.getId())
                        .filterFields(checkedFieldIds)
                        .id(menuDetailQueryReq.getId())
                        .version(menuDetailQueryReq.getVersionNo())
                        .history(menuDetailQueryReq.getHistory())
                        .showVersionCompare(menuDetailQueryReq.getShowVersionCompare())
                        .btnType(menuDetailQueryReq.getBtnType())
                        .build();

        List<FieldWordCloudDTO> worldCloudConfigs =
                configApi.getWordCloudFields(AppContext.getProjectNo(), AppContext.getSubjectId());

        Map<String, FieldWordCloudDTO> nameToWorldConfMap =
                worldCloudConfigs.stream()
                        .collect(
                                Collectors.toMap(
                                        ele -> ele.getTableName() + "." + ele.getColumnName(),
                                        v -> v));

        NestedModuleDataDTO detailData = null;

        if (dynamicDetailQueryReq.getId() > 0) {
            if (!CollectionUtils.isEmpty(checkedFieldIds)) {
                detailData = dataEngineApi.getDetail(dynamicDetailQueryReq);
            }
        }

        // 当前步骤能否编辑，有审批链时生效，无审批链时权限和模块权限一致
        Boolean canEdit = dynamicDetailQueryReq.getId() <= 0 ? false : true;

        // 申请权限能否生效
        Boolean canApply = dynamicDetailQueryReq.getId() <= 0 ? true : false;

        if (Objects.nonNull(detailData)) {
            if (hasApproval) {
                Long businessStatusValue =
                        detailData
                                                .getRecord()
                                                .get(0)
                                                .getRow()
                                                .get(
                                                        rootNode.getRootModule().getPrimaryTable()
                                                                + "."
                                                                + "business_status")
                                        == null
                                ? 0L
                                : Long.parseLong(
                                        String.valueOf(
                                                detailData
                                                        .getRecord()
                                                        .get(0)
                                                        .getRow()
                                                        .get(
                                                                rootNode.getRootModule()
                                                                                .getPrimaryTable()
                                                                        + "."
                                                                        + "business_status")));
                Long createById =
                        detailData
                                                .getRecord()
                                                .get(0)
                                                .getRow()
                                                .get(
                                                        rootNode.getRootModule().getPrimaryTable()
                                                                + "."
                                                                + "created_by")
                                        == null
                                ? 0L
                                : Long.parseLong(
                                        String.valueOf(
                                                detailData
                                                        .getRecord()
                                                        .get(0)
                                                        .getRow()
                                                        .get(
                                                                rootNode.getRootModule()
                                                                                .getPrimaryTable()
                                                                        + "."
                                                                        + "created_by")));
                if (businessStatusValue == 1) {
                    log.debug("数据id:{},创建人id:{}为草稿数据", dynamicDetailQueryReq.getId(), createById);

                    // 草稿只有创建人能编辑
                    canEdit = UserContext.getUserId().equals(createById);
                } else if (dynamicDetailQueryReq.getId() > 0) {
                    canEdit =
                            iApprovalEngine.getCanEdit(
                                    rootModule.getId(),
                                    dynamicDetailQueryReq.getId(),
                                    dynamicDetailQueryReq.getBtnType());
                    log.debug("从审批链获取id:{}当前步骤编辑权限:{}", dynamicDetailQueryReq.getId(), canEdit);
                }
            }
        }

        // 构建模块元数据配置列表 (ModuleMetaDTO)
        ModuleMetaDTO metaDTO =
                buildModuleMetaList(
                        rootModule.getId(),
                        null,
                        roleId,
                        permMap,
                        moduleIdHeaderMap,
                        nameToWorldConfMap,
                        rootNode,
                        canEdit,
                        canApply);

        return DetailDataResp.of(metaDTO, detailData);
    }

    @Override
    public List<VersionListResp> getVersionList(MenuVersionListQueryReq menuDetailQueryReq) {

        if (menuDetailQueryReq.getMenuId() == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }
        if (menuDetailQueryReq.getId() == null) {
            throw new IllegalArgumentException("单据 ID 不能为空");
        }

        List<SysMenuResp> menuList = sysMenuApi.list(1);

        if (CollectionUtils.isEmpty(menuList)) {
            return null;
        }

        SysMenuResp targetMenus =
                menuList.stream()
                        .filter(ele -> menuDetailQueryReq.getMenuId().equals(ele.getId()))
                        .findFirst()
                        .orElse(null);

        if (Objects.isNull(targetMenus)) {
            return null;
        }

        Boolean hasApproval =
                sysApprovalChainConfigApi.getApprovalStatus(List.of(targetMenus.getModuleId()));

        if (!hasApproval) {
            return List.of();
        }

        VersionListQueryReq versionListQueryReq =
                VersionListQueryReq.builder()
                        .id(menuDetailQueryReq.getId())
                        .moduleId(targetMenus.getModuleId())
                        .businessNo(menuDetailQueryReq.getBusinessNo())
                        .btnType(menuDetailQueryReq.getBtnType())
                        .build();

        return dataEngineApi.getVersionList(versionListQueryReq);
    }

    @Override
    public CreatedUserInfoResp getCreatedUserInfo(Long menuId, Long id, String businessNo) {
        if (Objects.isNull(menuId) || menuId <= 0) {
            throw new IllegalArgumentException("菜单ID不能为空");
        }

        if (Objects.isNull(id) || id <= 0) {
            throw new IllegalArgumentException("数据ID不能为空");
        }

        List<SysMenuResp> menuList = sysMenuApi.list(1);

        if (CollectionUtils.isEmpty(menuList)) {
            return null;
        }

        SysMenuResp targetMenu =
                menuList.stream()
                        .filter(ele -> menuId.equals(ele.getId()))
                        .findFirst()
                        .orElse(null);

        if (Objects.isNull(targetMenu)) {
            throw new BusinessException("菜单 [" + menuId + "] 没有对应的菜单数据");
        }

        Long moduleId = targetMenu.getModuleId();

        return dataEngineApi.getCreatedUserInfo(moduleId, id, businessNo);
    }

    private ModuleMetaDTO buildModuleMetaList(
            Long rootModuleId,
            Long menuId,
            Long roleId,
            Map<Long, ModulePermissionDTO> permMap,
            Map<Long, MenuHeaderColumnResp> moduleIdHeaderMap,
            Map<String, FieldWordCloudDTO> worldCloudConfigMap,
            ModuleTreeDTO moduleTreeDTO,
            Boolean canEdit,
            Boolean canApply) {
        if (moduleTreeDTO == null || moduleTreeDTO.getModuleMap() == null) {
            return null;
        }

        ModuleMetaDTO.ModuleMetaDTOBuilder parentModuleMeta = ModuleMetaDTO.builder();
        parentModuleMeta.moduleId(rootModuleId).menuId(menuId);

        ModuleInfo parentModuleInfo = moduleTreeDTO.getModuleMap().get(rootModuleId);
        Map<String, FieldMetaDTO> fieldMetaMap = new LinkedHashMap<>();
        ModulePermissionDTO modPerm = permMap != null ? permMap.get(rootModuleId) : null;

        if (parentModuleInfo.getFields() != null) {
            String primaryTable = parentModuleInfo.getPrimaryTable();
            for (ModuleFieldDTO f : parentModuleInfo.getFields()) {
                String col = f.getColumnName();
                String fullKey =
                        (primaryTable != null && !primaryTable.isBlank())
                                ? primaryTable + "." + col
                                : col;

                ModulePermissionDTO.FieldPermissionItem fp =
                        (modPerm != null && modPerm.getFieldPermissions() != null)
                                ? modPerm.getFieldPermissions().get(f.getId())
                                : null;

                FieldMetaDTO fMeta =
                        FieldMetaDTO.builder()
                                .id(f.getId())
                                .displayName(f.getDisplayName())
                                .apply(fp != null ? fp.getApply() && canApply : false)
                                .view(fp != null ? fp.getView() : false)
                                .edit(fp != null ? fp.getEdit() && canEdit : false)
                                .businessNo(
                                        worldCloudConfigMap.containsKey(fullKey)
                                                ? worldCloudConfigMap
                                                        .get(fullKey)
                                                        .getRelationBusinessNo()
                                                : null)
                                .build();

                fieldMetaMap.put(fullKey, fMeta);
            }
        }

        parentModuleMeta.fields(fieldMetaMap);

        ModuleSimplePermissionDTO permission = null;

        if (permMap != null && permMap.get(rootModuleId) != null) {
            ModulePermissionDTO p = permMap.get(rootModuleId);
            permission =
                    ModuleSimplePermissionDTO.builder()
                            .apply(Boolean.TRUE.equals(p.getApply()) ? 1 : 0)
                            .view(Boolean.TRUE.equals(p.getView()) ? 1 : 0)
                            .edit(Boolean.TRUE.equals(p.getEdit()) ? 1 : 0)
                            .build();
        }

        parentModuleMeta.permission(permission);

        List<ModuleMetaDTO> childrenMeta = new ArrayList<>();

        if (!CollectionUtils.isEmpty(moduleTreeDTO.getParentToChildren().get(rootModuleId))) {
            for (Long modId : moduleTreeDTO.getParentToChildren().get(rootModuleId)) {
                ModuleInfo childModInfo = moduleTreeDTO.getModuleMap().get(modId);
                ;
                if (childModInfo == null) {
                    continue;
                }
                childrenMeta.add(
                        buildModuleMetaList(
                                modId,
                                null,
                                roleId,
                                permMap,
                                moduleIdHeaderMap,
                                worldCloudConfigMap,
                                moduleTreeDTO,
                                canEdit,
                                canApply));
            }
        }

        parentModuleMeta.children(childrenMeta);

        parentModuleMeta.header(moduleIdHeaderMap.get(rootModuleId));

        return parentModuleMeta.build();
    }

    /**
     * targetIds只会一同属于其中的一颗子树 森林：多棵树，节点无parentId；根据目标id集合，找出所有命中树的根节点
     *
     * @param forest 森林（顶层根节点列表）
     * @param targetIds 目标id集合
     * @return 命中的根节点集合（自动去重）
     */
    public SysModuleSimpleTreeResp findRootNodesByTargetIds(
            List<SysModuleSimpleTreeResp> forest, Set<Long> targetIds) {
        if (forest == null || forest.isEmpty() || targetIds == null || targetIds.isEmpty()) {
            return null;
        }

        for (SysModuleSimpleTreeResp treeRoot : forest) {
            // 获取当前这棵树下全部节点ID
            Set<Long> allNodeIdsOfTree = getAllNodeIdsInTree(treeRoot);
            // 判断是否存在交集
            boolean hasHit = allNodeIdsOfTree.containsAll(targetIds);
            if (hasHit) {
                return treeRoot;
            }
        }

        return null;
    }

    /** DFS遍历单棵树，收集该树下所有节点id */
    private Set<Long> getAllNodeIdsInTree(SysModuleSimpleTreeResp root) {
        Set<Long> idSet = new HashSet<>();
        dfsCollectId(root, idSet);
        return idSet;
    }

    private void dfsCollectId(SysModuleSimpleTreeResp node, Set<Long> idSet) {
        if (node == null) {
            return;
        }
        idSet.add(node.getId());
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            for (SysModuleSimpleTreeResp child : node.getChildren()) {
                dfsCollectId(child, idSet);
            }
        }
    }

    public NestedModuleDataDTO buildTemplateTree(ModuleMetaDTO metaData) {
        return convertRootModuleMetaToTemplateData(metaData);
    }

    /**
     * 构造顶层模块的data数据模板
     *
     * @param metaData
     * @return
     */
    private NestedModuleDataDTO convertRootModuleMetaToTemplateData(ModuleMetaDTO metaData) {
        NestedModuleDataDTO.NestedModuleDataDTOBuilder nestedModuleDataDTOBuilder =
                NestedModuleDataDTO.builder();
        nestedModuleDataDTOBuilder.moduleId(metaData.getModuleId());

        nestedModuleDataDTOBuilder.record(new ArrayList<>());
        return nestedModuleDataDTOBuilder.build();
    }

    /**
     * 构造顶层模块到子模块的嵌套层级data数据模板
     *
     * @param metaData
     * @return
     */
    private NestedModuleDataDTO convertMetaToTemplateData(ModuleMetaDTO metaData) {
        NestedModuleDataDTO.NestedModuleDataDTOBuilder nestedModuleDataDTOBuilder =
                NestedModuleDataDTO.builder();
        nestedModuleDataDTOBuilder.moduleId(metaData.getModuleId());

        RecordDataDTO.RecordDataDTOBuilder recordDataDTOBuilder = RecordDataDTO.builder();

        // 递归子模块
        List<NestedModuleDataDTO> childTemplateList = new ArrayList<>();
        if (metaData.getChildren() != null) {
            for (ModuleMetaDTO childConfig : metaData.getChildren()) {
                NestedModuleDataDTO childVo = convertMetaToTemplateData(childConfig);
                childTemplateList.add(childVo);
            }
        }
        recordDataDTOBuilder.children(childTemplateList);

        Map<String, Object> rowMap = new HashMap<>();

        // 过滤掉没有创建权限的字段，将有权限字段填充空值返回
        metaData.getFields().entrySet().stream()
                .filter(ele -> ele.getValue().getApply())
                .forEach(
                        ele -> {
                            rowMap.put(ele.getKey(), null);
                        });

        recordDataDTOBuilder.row(rowMap);

        nestedModuleDataDTOBuilder.record(Collections.singletonList(recordDataDTOBuilder.build()));
        return nestedModuleDataDTOBuilder.build();
    }
}
