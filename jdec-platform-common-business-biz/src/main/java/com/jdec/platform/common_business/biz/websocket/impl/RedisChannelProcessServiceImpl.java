package com.jdec.platform.common_business.biz.websocket.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.common_business.api.dto.response.MenuListResp;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.ConfigCenterApiOpenFeignClient;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ModuleResp;
import com.jdec.platform.common_business.biz.service.ConfigCenterApiService;
import com.jdec.platform.common_business.biz.websocket.RedisChannelProcessService;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

// 根据moduleId得到最终受影响的页面绑定的moduleId集合
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisChannelProcessServiceImpl implements RedisChannelProcessService {

    private final ConfigCenterApiService configCenterApiService;

    private final ConfigCenterApiOpenFeignClient configCenterApiClient;

    @Override
    public Set<Long> findFinalNotifyModule(Long subjectId, List<Long> moduleIds, Long roleId) {
        if (CollectionUtils.isEmpty(moduleIds)) {
            return Set.of();
        }
        log.debug(
                "获取权限变化最终受影响的菜单和模块,消息体中主体id:{} , 模块id:{} , 角色id:{}", subjectId, moduleIds, roleId);
        ConfigApiResponse<List<MenuListResp>> response =
                configCenterApiClient.getMenuList(subjectId, null);

        if (!ConfigApiResponse.isSuccessResponse(response)) {
            return Set.of();
        }
        List<MenuListResp> menuRespList = response.getData();
        if (CollectionUtil.isEmpty(menuRespList)) {
            return Set.of();
        }

        // 筛选业务菜单，去掉系统设置菜单
        List<MenuListResp> bizMenuList = new ArrayList<>();
        bizMenuList.addAll(menuRespList);
        bizMenuList.sort(Comparator.comparing(MenuListResp::getSortOrder));

        Set<Long> notifyModules = new HashSet<>();

        List<ModuleResp> moduleRespList = configCenterApiService.getModuleList(subjectId, true);
        for (Long moduleId : moduleIds) {
            Set<Long> targetModules = findFirstMatchUpParent(bizMenuList, moduleId);

            Map<Long, List<ModuleResp>> groupByDetailModule =
                    moduleRespList.stream()
                            .filter(
                                    ele ->
                                            Objects.nonNull(ele.getDetailModuleId())
                                                    && "LIST".equals(ele.getModuleType()))
                            .collect(Collectors.groupingBy(ModuleResp::getDetailModuleId));

            for (Long menuModuleId : targetModules) {
                // 模块作为其他模块的详情模块参与权限判断
                if (groupByDetailModule.containsKey(menuModuleId)) {
                    Set<Long> listModules =
                            groupByDetailModule.get(menuModuleId).stream()
                                    .map(ele -> ele.getId().longValue())
                                    .collect(Collectors.toSet());
                    Set<String> moduleNames =
                            groupByDetailModule.get(menuModuleId).stream()
                                    .map(ele -> ele.getModuleName())
                                    .collect(Collectors.toSet());
                    log.debug(
                            "模块id:{}作为详情模块影响其他模块id:{}，模块名称:{}的权限，将其他列表模块也加入通知范围。",
                            menuModuleId,
                            listModules,
                            moduleNames);
                    notifyModules.addAll(listModules);
                }
            }

            notifyModules.addAll(targetModules);
        }

        log.debug("最终需要通知的角色Id:{} ， 模块id:{}", roleId, notifyModules);

        return notifyModules;
    }

    /**
     * 向上查找：第一个满足条件的父节点（整条上级链路只返回第一个匹配）
     *
     * @param allFlatList 数据库全部节点扁平集合
     * @param targetId 起始节点ID
     * @return 匹配节点 / null
     */
    private Set<Long> findFirstMatchUpParent(List<MenuListResp> allFlatList, Long targetId) {
        List<MenuListResp> targets = findNodeByModuleId(allFlatList, targetId);

        if (CollectionUtils.isEmpty(targets)) {
            return Set.of();
        }

        List<MenuListResp> targetParents = new ArrayList<>();
        for (MenuListResp menuResp : targets) {
            if (menuResp.getMenuType().equals(2)) {
                log.debug("模块id:{}，菜单名称:{}自身是页面菜单，将其放入结果列表。", targetId, menuResp.getTitle());
                targetParents.add(menuResp);
                continue;
            }
            log.debug("模块id:{}匹配的菜单名称:{}开始查找", targetId, menuResp.getTitle());
            Long pid = menuResp.getPid();
            // 逐级往上循环
            while (pid != null && pid != 0) {
                List<MenuListResp> targetParent = findNodeByMenuId(allFlatList, pid);
                if (CollectionUtils.isEmpty(targetParent)) {
                    break;
                }
                MenuListResp parent = targetParent.get(0);
                // 满足条件直接返回
                if (parent.getMenuType().equals(2)) {
                    log.debug(
                            "找到父级的页面菜单，菜单名称:{},菜单绑定的模块id:{}",
                            parent.getTitle(),
                            parent.getModuleId());
                    targetParents.add(parent);
                    break;
                }
                // 继续上一层
                pid = parent.getPid();
            }
        }

        return targetParents.stream().map(MenuListResp::getModuleId).collect(Collectors.toSet());
    }

    private List<MenuListResp> findNodeByModuleId(List<MenuListResp> allFlatList, Long targetId) {
        return allFlatList.stream().filter(n -> targetId.equals(n.getModuleId())).toList();
    }

    private List<MenuListResp> findNodeByMenuId(List<MenuListResp> allFlatList, Long targetId) {
        return allFlatList.stream().filter(n -> targetId.equals(n.getId())).toList();
    }
}
