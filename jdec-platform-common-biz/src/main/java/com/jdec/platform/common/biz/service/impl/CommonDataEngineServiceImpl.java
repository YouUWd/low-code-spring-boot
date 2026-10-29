package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.common.api.UserMenuHeaderApi;
import com.jdec.platform.common.api.dto.request.ChangeRequestDetailQueryReq;
import com.jdec.platform.common.api.dto.request.MenuDetailQueryReq;
import com.jdec.platform.common.api.dto.request.MenuVersionListQueryReq;
import com.jdec.platform.common.api.dto.response.*;
import com.jdec.platform.common.api.dto.response.DetailDataResp;
import com.jdec.platform.common.api.dto.response.FieldMetaDTO;
import com.jdec.platform.common.api.dto.response.ModuleMetaDTO;
import com.jdec.platform.common.api.dto.response.ModuleSimplePermissionDTO;
import com.jdec.platform.common.biz.QueryModel;
import com.jdec.platform.common.biz.service.CommonDataEngineService;
import com.jdec.platform.config.api.SysApprovalChainConfigApi;
import com.jdec.platform.config.api.SysFieldApi;
import com.jdec.platform.config.api.SysMenuApi;
import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.dto.common.*;
import com.jdec.platform.config.api.dto.response.*;
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

    private final SysFieldApi sysFieldApi;

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

        // 版本对比为传递默认为true
        if (Objects.isNull(menuDetailQueryReq.getShowVersionCompare())) {
            menuDetailQueryReq.setShowVersionCompare(true);
        }

        Long userId =
                Objects.nonNull(UserContext.getMockUserId())
                        ? UserContext.getMockUserId()
                        : UserContext.getRealUserId();
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
                                .getModuleFieldPermissions()
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
                        .showVersionCompare(
                                hasApproval && menuDetailQueryReq.getShowVersionCompare())
                        .latestWordData(
                                Objects.isNull(menuDetailQueryReq.getLatestWordData())
                                        || menuDetailQueryReq.getLatestWordData())
                        .btnType(menuDetailQueryReq.getBtnType())
                        .build();

        if ("apply-change".equals(menuDetailQueryReq.getBtnType())
                && menuDetailQueryReq
                        instanceof ChangeRequestDetailQueryReq changeRequestDetailQueryReq) {
            dynamicDetailQueryReq.setChangeRequestId(
                    changeRequestDetailQueryReq.getChangeRequestId());
        }

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

        // 当前请求的查看模式，新建或编辑或仅查看或者是变更申请
        QueryModel queryModel =
                dynamicDetailQueryReq.getId() <= 0 ? QueryModel.CREATE : QueryModel.EDIT;

        if (Objects.nonNull(detailData) && !CollectionUtils.isEmpty(detailData.getRecord())) {
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
                    boolean canEdit = userId.equals(createById);

                    // 等待提交状态使用新建权限
                    queryModel = canEdit ? QueryModel.CREATE : QueryModel.VIEW;
                } else if (businessStatusValue == 99 || businessStatusValue == -99) {
                    // 已启用或取消的单据，只能查看
                    queryModel = QueryModel.VIEW;
                } else if (dynamicDetailQueryReq.getId() > 0) {
                    // 审批流程中，判断当前步骤是否有操作权限(可编辑)
                    boolean canOperate =
                            iApprovalEngine.getCanEdit(
                                    rootModule.getId(),
                                    dynamicDetailQueryReq.getId(),
                                    dynamicDetailQueryReq.getBtnType());

                    if (!canOperate) {
                        queryModel = QueryModel.VIEW;
                    } else {
                        queryModel = QueryModel.EDIT;
                    }

                    log.debug("从审批链获取id:{}当前步骤编辑权限:{}", dynamicDetailQueryReq.getId(), canOperate);
                }
            }
        }

        // 获取SysField列表并按id映射
        Map<Long, SysFieldResp> fieldIdMap =
                sysFieldApi
                        .getValidSysFields(
                                AppContext.getProjectNo(), AppContext.getSubjectId(), null)
                        .stream()
                        .collect(Collectors.toMap(SysFieldResp::getId, v -> v));

        // 按字段名映射
        Map<String, SysFieldResp> fieldNameMap =
                fieldIdMap.entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        e ->
                                                e.getValue().getTableName()
                                                        + "."
                                                        + e.getValue().getColumnName(),
                                        Map.Entry::getValue));

        if ("apply-change".equals(menuDetailQueryReq.getBtnType())) {
            queryModel = QueryModel.CHANGE_REQUEST;
        } else if ("change".equals(menuDetailQueryReq.getBtnType())) {
            // 变更走新建权限
            queryModel = QueryModel.CREATE;
        }

        // 构建模块元数据配置列表 (ModuleMetaDTO)
        ModuleMetaDTO metaDTO =
                buildModuleMetaList(
                        rootModule.getId(),
                        null,
                        permMap,
                        moduleIdHeaderMap,
                        nameToWorldConfMap,
                        fieldNameMap,
                        rootNode,
                        queryModel);

        if (dynamicDetailQueryReq.getId() <= 0) {
            detailData = buildTemplateTree(metaDTO);
        }

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

    @Override
    public ChangeRequestResp getChangeRequestDetailByMenu(
            ChangeRequestDetailQueryReq changeRequestDetailQueryReq) {
        ChangeRequestResp.ChangeRequestRespBuilder builder = ChangeRequestResp.builder();

        //        MenuDetailQueryReq menuDetailQueryReq = MenuDetailQueryReq.builder().build();
        //        BeanUtils.copyProperties(changeRequestDetailQueryReq, menuDetailQueryReq);
        //
        //        menuDetailQueryReq.setShowVersionCompare(true);
        //        menuDetailQueryReq.setLatestWordData(false);

        changeRequestDetailQueryReq.setShowVersionCompare(true);
        changeRequestDetailQueryReq.setLatestWordData(false);

        DetailDataResp<NestedModuleDataDTO> detailDataResp =
                getDetailByMenu(changeRequestDetailQueryReq);

        if (changeRequestDetailQueryReq.getChangeRequestId() <= 0) {
            log.debug("变更申请新建。直接返回原版本的详情数据。");
        } else {
            log.debug("变更申请详情查看。查询变更申请单数据返回");
            if (Objects.nonNull(detailDataResp) && Objects.nonNull(detailDataResp.getData())) {
                Long rootModuleId = detailDataResp.getData().getModuleId();
                ModuleTreeDTO rootNode =
                        configApi.getModuleTree(AppContext.getProjectNo(), null, rootModuleId);
                String tableName = rootNode.getRootModule().getPrimaryTable() + "_change";
                Map<String, Object> changeRequestData =
                        dataEngineApi.getRecordDataByName(
                                tableName, changeRequestDetailQueryReq.getChangeRequestId());
                builder.changeRequestData(changeRequestData);
            }
        }

        return builder.detailData(detailDataResp).build();
    }

    private ModuleMetaDTO buildModuleMetaList(
            Long rootModuleId,
            Long menuId,
            Map<Long, ModulePermissionDTO> permMap,
            Map<Long, MenuHeaderColumnResp> moduleIdHeaderMap,
            Map<String, FieldWordCloudDTO> worldCloudConfigMap,
            Map<String, SysFieldResp> fieldNameMap,
            ModuleTreeDTO moduleTreeDTO,
            QueryModel queryModel) {
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
                        (modPerm != null && modPerm.getModuleFieldPermissions() != null)
                                ? modPerm.getModuleFieldPermissions().get(f.getId())
                                : (modPerm != null
                                                && modPerm.getModuleTreeFieldPermissions() != null)
                                        ? modPerm.getModuleTreeFieldPermissions().get(f.getId())
                                        : null;

                // 申请阶段，使用申请和查看权限 编辑阶段，使用编辑和查看权限 启用阶段，使用查看权限
                Integer permissionCode = 0;
                if (queryModel == QueryModel.CREATE) {
                    permissionCode = fp != null ? (fp.getApply() ? 2 : fp.getView() ? 1 : 0) : 0;
                } else if (queryModel == QueryModel.EDIT) {
                    permissionCode = fp != null ? (fp.getEdit() ? 2 : fp.getView() ? 1 : 0) : 0;
                } else if (queryModel == QueryModel.VIEW) {
                    permissionCode = fp != null ? (fp.getView() ? 1 : 0) : 0;
                } else if (queryModel == QueryModel.CHANGE_REQUEST) {
                    permissionCode = fp != null ? (fp.getEdit() || fp.getView() ? 2 : 0) : 0;
                }

                FieldMetaDTO fMeta =
                        FieldMetaDTO.builder()
                                .id(f.getId())
                                .displayName(f.getDisplayName())
                                .apply(fp != null ? fp.getApply() : false)
                                .view(fp != null ? fp.getView() : false)
                                .edit(fp != null ? fp.getEdit() : false)
                                .permissionCode(permissionCode)
                                .businessNo(
                                        worldCloudConfigMap.containsKey(fullKey)
                                                ? worldCloudConfigMap
                                                        .get(fullKey)
                                                        .getRelationBusinessNo()
                                                : null)
                                .businessTypeCode(
                                        fieldNameMap.containsKey(fullKey)
                                                ? fieldNameMap.get(fullKey).getBusinessTypeCode()
                                                : null)
                                .build();

                fieldMetaMap.put(fullKey, fMeta);
            }
        }

        parentModuleMeta.fields(fieldMetaMap);

        ModuleSimplePermissionDTO permission = null;

        if (permMap != null && permMap.get(rootModuleId) != null) {

            ModulePermissionDTO mp = permMap.get(rootModuleId);

            Integer modulePermissionCode = 0;
            if (queryModel == QueryModel.CREATE) {
                modulePermissionCode = mp != null ? (mp.getApply() ? 2 : mp.getView() ? 1 : 0) : 0;
            } else if (queryModel == QueryModel.EDIT) {
                modulePermissionCode = mp != null ? (mp.getEdit() ? 2 : mp.getView() ? 1 : 0) : 0;
            } else if (queryModel == QueryModel.VIEW) {
                modulePermissionCode = mp != null ? (mp.getView() ? 1 : 0) : 0;
            } else if (queryModel == QueryModel.CHANGE_REQUEST) {
                modulePermissionCode = mp != null ? (mp.getEdit() || mp.getView() ? 2 : 0) : 0;
            }
            permission =
                    ModuleSimplePermissionDTO.builder()
                            .apply(Boolean.TRUE.equals(mp.getApply()) ? 1 : 0)
                            .view(Boolean.TRUE.equals(mp.getView()) ? 1 : 0)
                            .edit(Boolean.TRUE.equals(mp.getEdit()) ? 1 : 0)
                            .permissionCode(modulePermissionCode)
                            .build();
        }

        parentModuleMeta.permission(permission);

        List<ModuleMetaDTO> childrenMeta = new ArrayList<>();

        if (!CollectionUtils.isEmpty(moduleTreeDTO.getParentToChildren().get(rootModuleId))) {
            for (Long modId : moduleTreeDTO.getParentToChildren().get(rootModuleId)) {
                ModuleInfo childModInfo = moduleTreeDTO.getModuleMap().get(modId);
                if (childModInfo == null) {
                    continue;
                }
                childrenMeta.add(
                        buildModuleMetaList(
                                modId,
                                null,
                                permMap,
                                moduleIdHeaderMap,
                                worldCloudConfigMap,
                                fieldNameMap,
                                moduleTreeDTO,
                                queryModel));
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
