package com.jdec.platform.dataengine.biz.service.impl;

import com.jdec.platform.dataengine.biz.entity.ModuleDataSnapshotContent;
import com.jdec.platform.dataengine.biz.mapper.ModuleDataSnapshotContentMapper;
import com.jdec.platform.dataengine.biz.service.IModuleDataSnapshotContentService;
import com.jdec.platform.shared.datasource.DataSource;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
@DataSource("company")
public class IModuleDataSnapshotContentServiceImpl implements IModuleDataSnapshotContentService {

    private final ModuleDataSnapshotContentMapper moduleDataSnapshotContentMapper;

    @Override
    public int save(ModuleDataSnapshotContent moduleDataChangeContent) {
        return moduleDataSnapshotContentMapper.insert(moduleDataChangeContent);
    }
}
