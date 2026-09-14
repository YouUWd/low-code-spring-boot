package com.jdec.platform.dataengine.biz.third.api.detail.impl;

import com.jdec.platform.dataengine.biz.dto.ModuleFilterInfo;
import com.jdec.platform.dataengine.biz.dto.ModuleInfo;
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
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
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
    public Map<String, Object> queryDetailBizRefer(DetailQueryContext context) {
        Map<String, Object> retrunMap = new HashMap<>();

        Boolean hasApproval =
                context.getQueryType() == QueryType.DETAIL_APPROVAL_CURRENT
                        || context.getQueryType() == QueryType.DETAIL_APPROVAL_HISTORY;

        ModuleFilterInfo moduleFilterInfo =
                ModuleFilterInfo.builder().moduleInfo(ModuleInfo.builder().build()).build();

        //        if (!hasApproval) {
        //            moduleFilterInfo =
        //                    moduleInfoWithPermissionService.filterModuleInfo(
        //                            context.getSubjectId(),
        //                            context.getRoleId(),
        //                            context.getModuleId(),
        //                            context.getBtnType(),
        //                            List.of());
        //        } else {
        //            moduleFilterInfo =
        //                    moduleInfoWithPermissionService.filterModuleInfoWithApproval(
        //                            context.getSubjectId(),
        //                            context.getRoleId(),
        //                            context.getModuleId(),
        //                            context.getBtnType(),
        //                            List.of());
        //        }

        //        ModuleType moduleType =
        //
        // ModuleType.getModuleType(moduleFilterInfo.getModuleInfo().getModuleType());

        ModuleType moduleType = ModuleType.DETAIL;

        if (moduleType == null) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                    ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
        }
        switch (moduleType) {
            case LIST -> {
                Map<Object, Object> listMap =
                        listBizQueryService.queryListBiz(ListQueryContext.builder().build());
                retrunMap.put(String.valueOf(context.getMenuId()), listMap);
            }
            case DETAIL -> {
                DetailModuleQueryContext detailModuleQueryContext =
                        DetailModuleQueryContext.builder().build();
                BeanUtils.copyProperties(context, detailModuleQueryContext);
                detailModuleQueryContext.setModuleId(context.getModuleId());

                Map<Object, Object> detailMap =
                        detailModuleDataQueryService.queryDetailModuleData(
                                detailModuleQueryContext);

                retrunMap.put("data", detailMap);

                // DataIntegrationContext 构建要区分是带否审批链 目前只有  context.getMenuId() 是带审批链的
                //                detailDataProcessFactory.processData(
                //                        (Map) retrunMap.get(context.getMenuId()),
                //                        retrunMap,
                //                        DataIntegrationContext.builder()
                //                                .subjectId(context.getSubjectId())
                //                                .roleId(context.getRoleId())
                //                                .queryType(context.getQueryType())
                //                                .id(context.getId())
                //                                .moduleFilterInfo(moduleFilterInfo)
                //                                .build());
            }
        }

        return retrunMap;
    }
}
