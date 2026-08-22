package com.jdec.platform.dataengine.biz.service;

import com.jdec.platform.dataengine.api.DataSnapshotApi;
import com.jdec.platform.dataengine.api.dto.request.DynamicSnapshotTriggerReq;
import com.jdec.platform.dataengine.api.dto.response.DataSnapshotResp;
import com.jdec.platform.dataengine.api.dto.response.VersionDiffResp;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SnapshotManagerService implements DataSnapshotApi {

    @Override
    public Long triggerSnapshot(DynamicSnapshotTriggerReq req) {
        // Implementation stub
        return 1L;
    }

    @Override
    public List<DataSnapshotResp> getSnapshots(Long moduleId, Long dataId) {
        // Implementation stub
        return Collections.emptyList();
    }

    @Override
    public VersionDiffResp diff(
            Long moduleId, Long dataId, Integer fromVersion, Integer toVersion) {
        // Implementation stub
        return new VersionDiffResp();
    }
}
