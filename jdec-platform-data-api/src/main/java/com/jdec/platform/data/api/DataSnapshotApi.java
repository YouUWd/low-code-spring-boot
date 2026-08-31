package com.jdec.platform.data.api;

import com.jdec.platform.data.api.dto.request.DynamicSnapshotTriggerReq;
import com.jdec.platform.data.api.dto.response.DataSnapshotResp;
import com.jdec.platform.data.api.dto.response.VersionDiffResp;
import java.util.List;

public interface DataSnapshotApi {
    Long triggerSnapshot(DynamicSnapshotTriggerReq req);

    List<DataSnapshotResp> getSnapshots(Long moduleId, Long dataId);

    VersionDiffResp diff(Long moduleId, Long dataId, Integer fromVersion, Integer toVersion);
}
