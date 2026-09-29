package com.jdec.platform.dataengine.biz.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.jdec.platform.dataengine.biz.entity.ModuleDataTmpSnapshotContent;
import java.util.List;

public interface IModuleDataTmpSnapshotContentService {

    public List<ModuleDataTmpSnapshotContent> query(Wrapper<ModuleDataTmpSnapshotContent> wrapper);
}
