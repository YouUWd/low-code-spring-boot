package com.jdec.platform.dataengine.biz.third.api.detail.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.biz.dto.MenuListResp;
import com.jdec.platform.dataengine.biz.dto.ModuleFilterInfo;
import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import com.jdec.platform.dataengine.biz.dto.context.DetailQueryContext;
import com.jdec.platform.dataengine.biz.entity.DataEngineSnapshot;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.enums.ModuleType;
import com.jdec.platform.dataengine.biz.enums.QueryType;
import com.jdec.platform.dataengine.biz.mapper.DataEngineSnapshotMapper;
import com.jdec.platform.dataengine.biz.third.api.data.DetailDataProcessFactory;
import com.jdec.platform.dataengine.biz.third.api.data.ListDataProcessFactory;
import com.jdec.platform.dataengine.biz.third.api.detail.DataSnapshotModuleService;
import com.jdec.platform.dataengine.biz.third.api.detail.DetailBizReferQueryService;
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
public class DetailBizHistoryQueryService implements DetailBizReferQueryService {

    private final MenuModuleMappingService menuModuleMappingService;

    private final ModuleInfoWithPermissionService moduleInfoWithPermissionService;

    private final Set<QueryType> TYPE_SET = Set.of(QueryType.DETAIL_APPROVAL_HISTORY);

    private final DataEngineSnapshotMapper dataEngineSnapshotMapper;

    private final DataSnapshotModuleService dataSnapshotModuleService;

    private final ListDataProcessFactory listDataProcessFactory;

    private final DetailDataProcessFactory detailDataProcessFactory;

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return TYPE_SET.contains(queryType);
    }

    @Override
    public List<RecordDataDTO> queryDetailBizRefer(DetailQueryContext context) {

        LambdaQueryWrapper<DataEngineSnapshot> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(DataEngineSnapshot::getDataId, context.getId());
        lambdaQueryWrapper.eq(DataEngineSnapshot::getVersionNo, context.getVersion());
        DataEngineSnapshot snapshot = dataEngineSnapshotMapper.selectOne(lambdaQueryWrapper);

        Map<Long, Object> moduleDataMap = dataSnapshotModuleService.extractModuleData(snapshot);

        List<MenuListResp> menuListRespList =
                menuModuleMappingService.findTargetAndAllChildrenByMenuId(context.getMenuId());
        if (CollectionUtil.isEmpty(menuListRespList)) {
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_REQUEST.getCode(),
                    ApiResponseCodeEnum.BAD_REQUEST.getMessage());
        }

        Map<Object, Object> retrunMap = new HashMap<>();

        Boolean hasApproval =
                context.getQueryType() == QueryType.DETAIL_APPROVAL_CURRENT
                        || context.getQueryType() == QueryType.DETAIL_APPROVAL_HISTORY;

        for (MenuListResp menuResp : menuListRespList) {
            ModuleFilterInfo moduleFilterInfo;

            if (hasApproval) {
                moduleFilterInfo =
                        moduleInfoWithPermissionService.filterModuleInfo(
                                context.getSubjectId(),
                                context.getRoleId(),
                                menuResp.getModuleId(),
                                context.getBtnType(),
                                List.of());
            } else {
                moduleFilterInfo =
                        moduleInfoWithPermissionService.filterModuleInfoWithApproval(
                                context.getSubjectId(),
                                context.getRoleId(),
                                menuResp.getModuleId(),
                                context.getBtnType(),
                                List.of());
            }

            ModuleType moduleType =
                    ModuleType.getModuleType(moduleFilterInfo.getModuleInfo().getModuleType());

            if (moduleType == null) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
                        ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage());
            }

            Object moduleData = moduleDataMap.get(menuResp.getModuleId());
            switch (moduleType) {
                case LIST -> {
                    Map rm = new HashMap();
                    listDataProcessFactory.processData(
                            (List) moduleData, rm, DataIntegrationContext.builder().build());
                    retrunMap.put(menuResp.getId(), rm);
                }
                case DETAIL -> {
                    Map rm = new HashMap();
                    detailDataProcessFactory.processData(
                            (Map) moduleData, rm, DataIntegrationContext.builder().build());
                    retrunMap.put(menuResp.getId(), rm);
                }
            }
        }

        return List.of();
    }
}
