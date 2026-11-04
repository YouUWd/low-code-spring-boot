package com.jdec.platform.common.biz.preprocess;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;

public interface SavePrepositionProcessorService {

    // 模块前置参数处理
    public void preProcess(NestedModuleDataDTO nestedModuleDataDTO);
}
