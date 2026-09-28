package com.jdec.platform.dataengine.biz.third.api.detail;

import com.jdec.platform.dataengine.biz.entity.DataEngineSnapshot;
import java.util.Map;

public interface DataSnapshotModuleService {

    public Map<Long, Object> extractModuleData(DataEngineSnapshot snapshot);
}
