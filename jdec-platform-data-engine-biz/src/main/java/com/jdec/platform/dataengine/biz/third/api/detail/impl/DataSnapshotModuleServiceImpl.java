package com.jdec.platform.dataengine.biz.third.api.detail.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.jdec.platform.dataengine.biz.dto.DataSnapshotModuleDTO;
import com.jdec.platform.dataengine.biz.entity.DataEngineSnapshot;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.enums.ModuleType;
import com.jdec.platform.dataengine.biz.third.api.detail.DataSnapshotModuleService;
import com.jdec.platform.dataengine.biz.util.JsonUtil;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataSnapshotModuleServiceImpl implements DataSnapshotModuleService {
    @Override
    public Map<Long, Object> extractModuleData(DataEngineSnapshot snapshot) {
        List<DataSnapshotModuleDTO> snapshotModuleDTOList = null;
        try {
            snapshotModuleDTOList =
                    JsonUtil.json2Obj(
                            snapshot.getJsonData(),
                            new TypeReference<List<DataSnapshotModuleDTO>>() {});
        } catch (Exception e) {
            throw new BusinessException(
                    ApiResponseCodeEnum.SERVER_ERROR.getCode(),
                    ApiResponseCodeEnum.SERVER_ERROR.getMessage(),
                    e);
        }
        // todo 根据 snapshotModuleDTO 生成 模块数据
        Map<Long, Object> result = new HashMap<>();
        if (CollectionUtil.isEmpty(snapshotModuleDTOList)) {
            return result;
        }

        for (DataSnapshotModuleDTO snapshotModuleDTO : snapshotModuleDTOList) {
            ModuleType moduleType = ModuleType.getModuleType(snapshotModuleDTO.getMType());

            switch (moduleType) {
                case DETAIL -> {
                    Map<String, Object> modData = new HashMap<>();
                    for (Map.Entry<String, List<Map<String, Object>>> entry :
                            snapshotModuleDTO.getModData().entrySet()) {
                        for (Map.Entry<String, Object> e : entry.getValue().get(0).entrySet()) {
                            modData.put(entry.getKey() + "." + e.getKey(), e.getValue());
                        }
                    }
                    result.put(snapshotModuleDTO.getMId(), modData);
                }
                case LIST -> {
                    List<Map<String, Object>> mapList = new ArrayList<>();
                    for (Map.Entry<String, List<Map<String, Object>>> entry :
                            snapshotModuleDTO.getModData().entrySet()) {

                        //                        for(Map<String,Object> mapdata:entry.getValue()){
                        //                            for(Map.Entry<String,Object>
                        // e:mapdata.entrySet()){
                        //
                        // modData.put(entry.getKey()+"."+e.getKey(),e.getValue());
                        //                            }
                        //                        }
                    }
                }
            }
        }
        return result;
    }
}
