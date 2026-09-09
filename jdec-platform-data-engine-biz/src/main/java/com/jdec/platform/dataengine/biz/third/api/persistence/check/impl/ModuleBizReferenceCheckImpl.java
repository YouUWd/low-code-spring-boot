package com.jdec.platform.dataengine.biz.third.api.persistence.check.impl;

import com.jdec.platform.dataengine.biz.dto.context.ModuleDataCheckContext;
import com.jdec.platform.dataengine.biz.enums.SaveType;
import com.jdec.platform.dataengine.biz.third.api.persistence.check.BaseDataSaveCheck;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModuleBizReferenceCheckImpl implements BaseDataSaveCheck {
    @Override
    public Boolean check(ModuleDataCheckContext context) {
        return null;
    }

    @Override
    public Boolean isReferTo(SaveType type) {
        return null;
    }
}
