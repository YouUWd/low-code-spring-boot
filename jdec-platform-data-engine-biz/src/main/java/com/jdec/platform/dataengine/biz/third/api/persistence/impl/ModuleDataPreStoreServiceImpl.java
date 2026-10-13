package com.jdec.platform.dataengine.biz.third.api.persistence.impl;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.dto.ModuleDataStoreDTO;
import com.jdec.platform.dataengine.biz.third.api.persistence.ModuleDataPreStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleDataPreStoreServiceImpl implements ModuleDataPreStoreService {
    @Override
    public Object preProcess(ModuleDataStoreDTO dataStoreDTO) {
        NestedModuleDataDTO moduleTreeNodeDataDTO = null;

        return moduleTreeNodeDataDTO;
    }
}
