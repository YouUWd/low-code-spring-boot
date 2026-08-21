package com.jdec.platform.dataengine.api;

import com.jdec.platform.dataengine.api.dto.request.DynamicSnapshotTriggerReq;
import com.jdec.platform.dataengine.api.dto.response.DataSnapshotResp;
import com.jdec.platform.dataengine.api.dto.response.VersionDiffResp;
import java.util.List;

public interface DataSnapshotApi {
    Long triggerSnapshot(DynamicSnapshotTriggerReq req);

    List<DataSnapshotResp> getSnapshots(Long moduleId, Long dataId);

    VersionDiffResp diff(Long moduleId, Long dataId, Integer fromVersion, Integer toVersion);
}
