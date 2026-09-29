package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.common.api.UserMenuHeaderApi;
import com.jdec.platform.common.biz.dto.request.MenuDetailQueryReq;
import com.jdec.platform.common.biz.service.CommonDataEngineService;
import com.jdec.platform.config.api.SysMenuApi;
import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.response.SysMenuResp;
import com.jdec.platform.config.api.dto.response.SysModuleSimpleTreeResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.dataengine.api.DataEngineApi;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.request.DynamicDetailQueryReq;
import com.jdec.platform.dataengine.api.dto.response.DetailDataResp;
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

    private final UserMenuHeaderApi userMenuHeaderApi;

    private final ConfigApi configApi;

    private final SysMenuApi sysMenuApi;

    private final SysModuleApi sysModuleApi;

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

        for (Long moduleId : moduleIds) {

            //            if (Objects.nonNull(menuDetailQueryReq.getFlatQuery())
            //                    && !menuDetailQueryReq.getFlatQuery()) {
            // 获取模块树
            ModuleTreeDTO tree =
                    configApi.getModuleTree(
                            AppContext.getProjectNo(), AppContext.getSubjectId(), moduleId);

            if (Objects.isNull(tree)) {
                log.debug("模块树查询结果未空，返回空列表。");
            }

            // TODO 模块下所有字段需要进行权限过滤
            checkedFieldIds.addAll(tree.getFieldMap().keySet());
            //            }
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

        return dataEngineApi.getDetail(dynamicDetailQueryReq);
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
