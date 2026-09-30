package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.common.api.UserMenuHeaderApi;
import com.jdec.platform.common.api.dto.response.FieldMetaDTO;
import com.jdec.platform.common.api.dto.response.ModuleMetaDTO;
import com.jdec.platform.common.api.dto.response.ModuleSimplePermissionDTO;
import com.jdec.platform.common.biz.dto.request.MenuDetailQueryReq;
import com.jdec.platform.common.biz.dto.request.MenuVersionListQueryReq;
import com.jdec.platform.common.biz.dto.response.DetailDataResp;
import com.jdec.platform.common.biz.service.CommonDataEngineService;
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
import com.jdec.platform.dataengine.api.dto.request.DynamicDetailQueryReq;
import com.jdec.platform.dataengine.api.dto.request.VersionListQueryReq;
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

        Set<Long> moduleIds =
                targetMenus.stream().map(SysMenuResp::getModuleId).collect(Collectors.toSet());

        List<SysModuleSimpleTreeResp> moduleTree =
                sysModuleApi.getAvailableModuleTree(
                        1, AppContext.getProjectNo(), AppContext.getSubjectId());

        SysModuleSimpleTreeResp rootModule = findRootNodesByTargetIds(moduleTree, moduleIds);

        if (Objects.isNull(rootModule)) {
            throw new BusinessException("菜单 [" + menuDetailQueryReq.getMenuIds() + "] 没有共同的父模块");
        }

        List<Long> checkedFieldIds = new ArrayList<>();

        Map<Long, MenuHeaderColumnResp> moduleIdHeaderMap = new HashMap<>();

        ModuleTreeDTO rootNode =
                configApi.getModuleTree(AppContext.getProjectNo(), null, rootModule.getId());

        for (SysMenuResp targetMenu : targetMenus) {

            List<Long> fieldIds = new ArrayList<>();

            MenuHeaderDisplayResp menuHeaderDisplayResp =
                    userMenuHeaderApi.getUserMenuHeaderDisplay(
                            UserContext.getUserId(), targetMenu.getId(), UserContext.getRoleId());

            if (Objects.nonNull(menuHeaderDisplayResp)) {
                moduleIdHeaderMap.put(targetMenu.getModuleId(), menuHeaderDisplayResp.getHeader());
            }

            if (Objects.isNull(rootNode)) {
                log.debug("模块树查询结果未空，返回空列表。");
            }

            fieldIds.addAll(
                    rootNode.getModuleMap().get(targetMenu.getModuleId()).getFields().stream()
                            .map(ModuleFieldDTO::getId)
                            .collect(Collectors.toSet()));

            // TODO 模块下所有字段需要进行权限过滤
            checkedFieldIds.addAll(fieldIds);
        }

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

        NestedModuleDataDTO detailData = dataEngineApi.getDetail(dynamicDetailQueryReq);

        Map<Long, ModulePermissionDTO> permMap =
                configApi.getModulePermissions(
                        roleId, new ArrayList<>(rootNode.getAllowedModuleIds()));

        List<FieldWordCloudDTO> worldCloudConfigs =
                configApi.getWordCloudFields(AppContext.getProjectNo(), AppContext.getSubjectId());

        Map<String, FieldWordCloudDTO> nameToWorldConfMap =
                worldCloudConfigs.stream()
                        .collect(
                                Collectors.toMap(
                                        ele -> ele.getTableName() + "." + ele.getColumnName(),
                                        v -> v));

        // 构建模块元数据配置列表 (ModuleMetaDTO)
        ModuleMetaDTO metaDTO =
                buildModuleMetaList(
                        rootModule.getId(),
                        null,
                        roleId,
                        permMap,
                        moduleIdHeaderMap,
                        nameToWorldConfMap,
                        rootNode);

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

        VersionListQueryReq versionListQueryReq =
                VersionListQueryReq.builder()
                        .moduleId(targetMenus.getModuleId())
                        .id(menuDetailQueryReq.getId())
                        .btnType(menuDetailQueryReq.getBtnType())
                        .build();

        return dataEngineApi.getVersionList(versionListQueryReq);
    }

    private ModuleMetaDTO buildModuleMetaList(
            Long rootModuleId,
            Long menuId,
            Long roleId,
            Map<Long, ModulePermissionDTO> permMap,
            Map<Long, MenuHeaderColumnResp> moduleIdHeaderMap,
            Map<String, FieldWordCloudDTO> worldCloudConfigMap,
            ModuleTreeDTO moduleTreeDTO) {
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
                                .apply(fp != null ? fp.getApply() : true)
                                .view(fp != null ? fp.getView() : true)
                                .edit(fp != null ? fp.getEdit() : true)
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
                                moduleTreeDTO));
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
}
