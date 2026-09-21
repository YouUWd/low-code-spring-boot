package com.jdec.platform.dataengine.biz.third.api.detail.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.biz.dto.context.DetailQueryContext;
import com.jdec.platform.dataengine.biz.enums.QueryType;
import com.jdec.platform.dataengine.biz.mapper.DataEngineSnapshotMapper;
import com.jdec.platform.dataengine.biz.third.api.data.DetailDataProcessFactory;
import com.jdec.platform.dataengine.biz.third.api.data.ListDataProcessFactory;
import com.jdec.platform.dataengine.biz.third.api.detail.DataSnapshotModuleService;
import com.jdec.platform.dataengine.biz.third.api.detail.DetailBizReferQueryService;
import com.jdec.platform.dataengine.biz.third.api.module.MenuModuleMappingService;
import com.jdec.platform.dataengine.biz.third.api.module.ModuleInfoWithPermissionService;
import java.io.File;
import java.io.IOException;
import java.util.List;
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

        ObjectMapper objectMapper = new ObjectMapper();

        File jsonFile = new File("D:\\历史数据.json");
        // readTree：直接读文件转JsonNode
        try {
            return objectMapper.readValue(jsonFile, new TypeReference<List<RecordDataDTO>>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        /**
         * LambdaQueryWrapper<DataEngineSnapshot> lambdaQueryWrapper = new LambdaQueryWrapper<>();
         * lambdaQueryWrapper.eq(DataEngineSnapshot::getDataId, context.getId());
         * lambdaQueryWrapper.eq(DataEngineSnapshot::getVersionNo, context.getVersion());
         * DataEngineSnapshot snapshot = dataEngineSnapshotMapper.selectOne(lambdaQueryWrapper);
         *
         * <p>Map<Long, Object> moduleDataMap =
         * dataSnapshotModuleService.extractModuleData(snapshot);
         *
         * <p>List<MenuListResp> menuListRespList =
         * menuModuleMappingService.findTargetAndAllChildrenByMenuId(context.getMenuId()); if
         * (CollectionUtil.isEmpty(menuListRespList)) { throw new BusinessException(
         * ApiResponseCodeEnum.BAD_REQUEST.getCode(), ApiResponseCodeEnum.BAD_REQUEST.getMessage());
         * }
         *
         * <p>Map<Object, Object> retrunMap = new HashMap<>();
         *
         * <p>Boolean hasApproval = context.getQueryType() == QueryType.DETAIL_APPROVAL_CURRENT ||
         * context.getQueryType() == QueryType.DETAIL_APPROVAL_HISTORY;
         *
         * <p>for (MenuListResp menuResp : menuListRespList) { ModuleFilterInfo moduleFilterInfo;
         *
         * <p>if (hasApproval) { moduleFilterInfo =
         * moduleInfoWithPermissionService.filterModuleInfo( context.getSubjectId(),
         * context.getRoleId(), menuResp.getModuleId(), context.getBtnType(), List.of()); } else {
         * moduleFilterInfo = moduleInfoWithPermissionService.filterModuleInfoWithApproval(
         * context.getSubjectId(), context.getRoleId(), menuResp.getModuleId(),
         * context.getBtnType(), List.of()); }
         *
         * <p>ModuleType moduleType =
         * ModuleType.getModuleType(moduleFilterInfo.getModuleInfo().getModuleType());
         *
         * <p>if (moduleType == null) { throw new BusinessException(
         * ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getCode(),
         * ApiResponseCodeEnum.BAD_MODULE_DATA_ERROR.getMessage()); }
         *
         * <p>Object moduleData = moduleDataMap.get(menuResp.getModuleId()); switch (moduleType) {
         * case LIST -> { Map rm = new HashMap(); listDataProcessFactory.processData( (List)
         * moduleData, rm, DataIntegrationContext.builder().build());
         * retrunMap.put(menuResp.getId(), rm); } case DETAIL -> { Map rm = new HashMap();
         * detailDataProcessFactory.processData( (Map) moduleData, rm,
         * DataIntegrationContext.builder().build()); retrunMap.put(menuResp.getId(), rm); } } }
         *
         * <p>return List.of();
         */
    }
}
