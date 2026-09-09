package com.jdec.platform.dataengine.biz.third.api.persistence.post.impl;

import com.jdec.platform.dataengine.biz.dto.context.ModuleDataPostContext;
import com.jdec.platform.dataengine.biz.enums.SaveType;
import com.jdec.platform.dataengine.biz.third.api.persistence.post.BaseDataSavePost;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class DataSaveChangeLogPostImpl implements BaseDataSavePost {
    @Override
    public Boolean postProcess(ModuleDataPostContext context) {
        return null;
    }

    @Override
    public Boolean isReferTo(SaveType type) {
        return null;
    }
}
