package com.jdec.platform.dataengine.biz.service.impl;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.request.DynamicDetailQueryReq;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.dto.context.ModuleDetailQueryContext;
import com.jdec.platform.dataengine.biz.service.DynamicQueryService;
import com.jdec.platform.dataengine.biz.service.ModuleBizDataService;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.UserContext;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class ModuleBizDataServiceImpl implements ModuleBizDataService {

    private final DynamicQueryService dynamicQueryService;

    @Override
    public NestedModuleDataDTO getModuleDetailDataAsync(ModuleDetailQueryContext queryContext) {
        CompletableFuture<NestedModuleDataDTO> nestedModuleDataFuture =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                AppContext.setContext(
                                        queryContext.getProjectNo(), queryContext.getSubjectId());
                                UserContext.setLoginUser(queryContext.getLoginUser());
                                return getModuleDetailData(queryContext);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            } finally {
                                AppContext.remove();
                                UserContext.remove();
                            }
                        });
        try {
            return nestedModuleDataFuture.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public NestedModuleDataDTO getModuleDetailData(ModuleDetailQueryContext queryContext) {
        // 查询 根模块及其子模的 信息
        Set<Long> filterField = new HashSet<>();
        // 把所有模块的字段加进去
        for (Long mId : queryContext.getModuleCacheMap().keySet()) {
            SysModuleCombineInfo sysModuleInfo = queryContext.getModuleCacheMap().get(mId);
            sysModuleInfo.getModuleInfo().getFields().stream()
                    .map(x -> x.getId())
                    .forEach(filterField::add);
        }
        // 终态需要切 快照保存
        NestedModuleDataDTO result = null;
        DynamicDetailQueryReq queryReq = new DynamicDetailQueryReq();
        queryReq.setModuleId(queryContext.getModuleId());
        queryReq.setId(queryContext.getDataId());
        queryReq.setVersion(queryContext.getVersionNo());
        queryReq.setFilterFields(new ArrayList<>(filterField));
        queryReq.setShowVersionCompare(false);
        List<NestedModuleDataDTO> nestedModuleDataDTOList =
                dynamicQueryService.getDetail(queryReq, null);
        result = nestedModuleDataDTOList.get(0);
        return result;
    }
}
