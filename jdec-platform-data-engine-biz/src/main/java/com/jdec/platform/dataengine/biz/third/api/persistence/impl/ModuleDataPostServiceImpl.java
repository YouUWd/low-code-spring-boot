package com.jdec.platform.dataengine.biz.third.api.persistence.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.api.DataSavePostApi;
import com.jdec.platform.dataengine.api.dto.model.DataPostParamDTO;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataPostService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleDataPostServiceImpl implements ModuleDataPostService {

    private final List<DataSavePostApi> dataSavePostApiList;

    @Override
    public void postProcessData(DataPostParamDTO dataPostParamDTO) {

        if (CollectionUtil.isNotEmpty(dataSavePostApiList)) {
            dataSavePostApiList.stream()
                    .filter(dataSavePostApi -> dataSavePostApi.isApplicableSystem(dataPostParamDTO))
                    .forEach(dataSavePostApi -> dataSavePostApi.postProcess(dataPostParamDTO));
        }
    }
}
