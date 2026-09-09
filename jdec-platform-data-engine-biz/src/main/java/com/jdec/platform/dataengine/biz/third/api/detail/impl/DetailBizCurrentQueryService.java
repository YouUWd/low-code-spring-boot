package com.jdec.platform.dataengine.biz.third.api.detail.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.biz.dto.MenuListResp;
import com.jdec.platform.dataengine.biz.dto.ModuleFilterInfo;
import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import com.jdec.platform.dataengine.biz.dto.context.DetailModuleQueryContext;
import com.jdec.platform.dataengine.biz.dto.context.DetailQueryContext;
import com.jdec.platform.dataengine.biz.dto.context.ListQueryContext;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.enums.ModuleType;
import com.jdec.platform.dataengine.biz.enums.QueryType;
import com.jdec.platform.dataengine.biz.third.api.ListBizQueryService;
import com.jdec.platform.dataengine.biz.third.api.data.DetailDataProcessFactory;
import com.jdec.platform.dataengine.biz.third.api.detail.DetailBizReferQueryService;
import com.jdec.platform.dataengine.biz.third.api.detail.DetailModuleDataQueryService;
import com.jdec.platform.dataengine.biz.third.api.module.MenuModuleMappingService;
import com.jdec.platform.dataengine.biz.third.api.module.ModuleInfoWithPermissionService;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DetailBizCurrentQueryService implements DetailBizReferQueryService {

    private final MenuModuleMappingService menuModuleMappingService;

    private final ModuleInfoWithPermissionService moduleInfoWithPermissionService;

    private final ListBizQueryService listBizQueryService;

    private final DetailModuleDataQueryService detailModuleDataQueryService;

    private final DetailDataProcessFactory detailDataProcessFactory;

    private final Set<QueryType> TYPE_SET =
            Set.of(QueryType.DETAIL, QueryType.DETAIL_APPROVAL_CURRENT);

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return TYPE_SET.contains(queryType);
    }

    @Override
    public Map<Object, Object> queryDetailBizRefer(DetailQueryContext context) {

        List<MenuListResp> menuListRespList =
                menuModuleMappingService.findTargetAndAllChildrenByMenuId(context.getMenuId());
        if (CollectionUtil.isEmpty(menuListRespList)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_REQUEST.getCode(),
                    ApiResponseCodeEnum.BAD_REQUEST.getMessage());
        }
        Map<Object, Object> retrunMap = new HashMap<>();

        for (MenuListResp menuResp : menuListRespList) {

            ModuleFilterInfo moduleFilterInfo =
                    moduleInfoWithPermissionService.filterModuleInfo(
                            context.getSubjectId(),
                            context.getRoleId(),
                            menuResp.getModuleId(),
                            context.getBtnType(),
                            context.getQueryType() == QueryType.DETAIL_APPROVAL_CURRENT
                                    ? Boolean.TRUE
                                    : Boolean.FALSE);

            ModuleType moduleType =
                    ModuleType.getModuleType(moduleFilterInfo.getModuleInfo().getModuleType());

            if (moduleType == null) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
            }
            switch (moduleType) {
                case LIST -> {
                    Map<Object, Object> listMap =
                            listBizQueryService.queryListBiz(ListQueryContext.builder().build());
                    retrunMap.put(menuResp.getId(), listMap);
                }
                case DETAIL -> {
                    Map<Object, Object> detailMap =
                            detailModuleDataQueryService.queryDetailModuleData(
                                    DetailModuleQueryContext.builder().build());

                    retrunMap.put(menuResp.getId(), detailMap);
                    // DataIntegrationContext 构建要区分是带否审批链 目前只有  context.getMenuId() 是带审批链的
                    detailDataProcessFactory.processData(
                            (Map) retrunMap.get(context.getMenuId()),
                            retrunMap,
                            DataIntegrationContext.builder().build());
                }
            }
        }

        return retrunMap;
    }
}
