package com.jdec.platform.dataengine.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.jdec.platform.dataengine.biz.entity.ModuleDataTmpSnapshotContent;
import com.jdec.platform.dataengine.biz.mapper.ModuleDataTmpSnapshotContentMapper;
import com.jdec.platform.dataengine.biz.service.IModuleDataTmpSnapshotContentService;
import com.jdec.platform.shared.datasource.DataSource;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
@DataSource("company")
public class IModuleDataTmpSnapshotContentServiceImpl
        implements IModuleDataTmpSnapshotContentService {

    private final ModuleDataTmpSnapshotContentMapper moduleDataTmpSnapshotContentMapper;

    @Override
    public List<ModuleDataTmpSnapshotContent> query(Wrapper<ModuleDataTmpSnapshotContent> wrapper) {
        return moduleDataTmpSnapshotContentMapper.selectList(wrapper);
    }
}
