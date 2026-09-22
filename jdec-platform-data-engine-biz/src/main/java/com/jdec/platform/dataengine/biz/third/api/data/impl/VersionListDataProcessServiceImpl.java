package com.jdec.platform.dataengine.biz.third.api.data.impl;

import com.jdec.platform.dataengine.api.dto.enums.QueryType;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import com.jdec.platform.dataengine.biz.mapper.DataEngineSnapshotMapper;
import com.jdec.platform.dataengine.biz.third.api.data.DetailDataProcessService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 详情接口版本列表数据处理 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VersionListDataProcessServiceImpl implements DetailDataProcessService {

    private final DataEngineSnapshotMapper dataEngineSnapshotMapper;

    private final Set<QueryType> TYPE_SET =
            Set.of(
                    QueryType.DETAIL_APPROVAL_CURRENT,
                    QueryType.DETAIL_APPROVAL_HISTORY,
                    QueryType.DETAIL_SUBTABLE_QUERY);

    @Override
    public void processAssembleData(
            List<NestedModuleDataDTO> records, DataIntegrationContext context) {

        /**
         * LambdaQueryWrapper<DataEngineSnapshot> lambdaQueryWrapper = new LambdaQueryWrapper<>();
         * lambdaQueryWrapper.eq( DataEngineSnapshot::getModuleId,
         * context.getModuleFilterInfo().getModuleInfo().getModuleId());
         * lambdaQueryWrapper.eq(DataEngineSnapshot::getDataId, context.getId());
         * List<DataEngineSnapshot> dataEngineSnapshotList =
         * dataEngineSnapshotMapper.selectList(lambdaQueryWrapper); if
         * (CollectionUtil.isNotEmpty(dataEngineSnapshotList)) { List<VersionInfo> versionInfoList =
         * new ArrayList<>(); for (DataEngineSnapshot ds : dataEngineSnapshotList) { VersionInfo
         * versionInfo = VersionInfo.builder() .recordId(ds.getDataId())
         * .statusValue(ds.getFinalStatusValue()) .versionNo(ds.getVersionNo())
         * .businessNo(ds.getBusinessNo()) .history(true) .build();
         * versionInfoList.add(versionInfo); } DataProcessUtils.fillMapUnitiveData( moduleData,
         * ResultSetConstantKey.RESULT_SET_KEY_VERSION_LIST, versionInfoList); }
         */
    }

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return TYPE_SET.contains(queryType);
    }
}
