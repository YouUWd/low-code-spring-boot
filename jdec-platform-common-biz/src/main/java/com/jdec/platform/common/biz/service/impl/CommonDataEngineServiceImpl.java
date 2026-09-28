package com.jdec.platform.common.biz.service.impl;

import com.jdec.platform.common.api.UserMenuHeaderApi;
import com.jdec.platform.common.biz.dto.request.MenuDetailQueryReq;
import com.jdec.platform.common.biz.service.CommonDataEngineService;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import com.jdec.platform.config.api.dto.response.MenuHeaderDisplayResp;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.dataengine.api.DataEngineApi;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.request.DynamicDetailQueryReq;
import com.jdec.platform.dataengine.api.dto.response.DetailDataResp;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 根据菜单查询详情数据 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommonDataEngineServiceImpl implements CommonDataEngineService {

    private final DataEngineApi dataEngineApi;

    private final UserMenuHeaderApi userMenuHeaderApi;

    private final ConfigApi configApi;

    @Override
    public DetailDataResp<NestedModuleDataDTO> getDetailByMenu(
            MenuDetailQueryReq menuDetailQueryReq) {

        if (menuDetailQueryReq.getMenuId() == null) {
            throw new IllegalArgumentException("菜单 ID 不能为空");
        }
        if (menuDetailQueryReq.getId() == null) {
            throw new IllegalArgumentException("单据 ID 不能为空");
        }

        Long userId = UserContext.getUserId();
        Long roleId = UserContext.getRoleId();

        // 1. 获取表头展示配置提取 rootModuleId
        MenuHeaderDisplayResp headerDisplay =
                userMenuHeaderApi.getUserMenuHeaderDisplay(
                        userId, menuDetailQueryReq.getMenuId(), roleId);
        if (headerDisplay == null || headerDisplay.getHeader() == null) {
            throw new BusinessException("菜单 [" + menuDetailQueryReq.getMenuId() + "] 未配置有效的表头展示信息");
        }

        MenuHeaderColumnResp headerRoot = headerDisplay.getHeader();
        Long rootModuleId = headerRoot.getModuleId();
        if (rootModuleId == null) {
            throw new BusinessException(
                    "菜单 [" + menuDetailQueryReq.getMenuId() + "] 对应的表头未关联有效的根模块 ID");
        }

        List<Long> checkedFieldIds = new ArrayList<>();
        if (Objects.nonNull(menuDetailQueryReq.getFlatQuery())
                && !menuDetailQueryReq.getFlatQuery()) {
            // 获取模块树
            ModuleTreeDTO tree =
                    configApi.getModuleTree(
                            AppContext.getProjectNo(), AppContext.getSubjectId(), rootModuleId);

            if (Objects.isNull(tree)) {
                log.debug("模块树查询结果未空，返回空列表。");
            }

            // TODO 模块下所有字段需要进行权限过滤
            checkedFieldIds.addAll(tree.getFieldMap().keySet());
        }

        DynamicDetailQueryReq dynamicDetailQueryReq =
                DynamicDetailQueryReq.builder()
                        .moduleId(rootModuleId)
                        .filterFields(checkedFieldIds)
                        .id(menuDetailQueryReq.getId())
                        .version(menuDetailQueryReq.getVersionNo())
                        .history(menuDetailQueryReq.getHistory())
                        .showVersionCompare(menuDetailQueryReq.getShowVersionCompare())
                        .btnType(menuDetailQueryReq.getBtnType())
                        .build();

        return dataEngineApi.getDetail(dynamicDetailQueryReq);
    }
}
