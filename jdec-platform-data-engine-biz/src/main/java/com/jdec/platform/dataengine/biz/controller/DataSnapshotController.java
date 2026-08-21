package com.jdec.platform.dataengine.biz.controller;

import com.jdec.platform.dataengine.api.DataSnapshotApi;
import com.jdec.platform.dataengine.api.dto.request.DynamicSnapshotTriggerReq;
import com.jdec.platform.dataengine.api.dto.response.DataSnapshotResp;
import com.jdec.platform.dataengine.api.dto.response.VersionDiffResp;
import com.jdec.platform.shared.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/data-engine/v1/modules")
@RequiredArgsConstructor
public class DataSnapshotController {

    private final DataSnapshotApi dataSnapshotApi;

    @PostMapping("/{moduleId}/trigger-snapshot")
    public ApiResponse<Long> triggerSnapshot(
            @PathVariable Long moduleId, @RequestBody DynamicSnapshotTriggerReq req) {
        req.setModuleId(moduleId);
        return ApiResponse.success(dataSnapshotApi.triggerSnapshot(req));
    }

    @GetMapping("/{moduleId}/snapshots/{dataId}")
    public ApiResponse<List<DataSnapshotResp>> getSnapshots(
            @PathVariable Long moduleId, @PathVariable Long dataId) {
        return ApiResponse.success(dataSnapshotApi.getSnapshots(moduleId, dataId));
    }

    @GetMapping("/{moduleId}/snapshots/{dataId}/diff")
    public ApiResponse<VersionDiffResp> diff(
            @PathVariable Long moduleId,
            @PathVariable Long dataId,
            @RequestParam Integer fromVersion,
            @RequestParam Integer toVersion) {
        return ApiResponse.success(dataSnapshotApi.diff(moduleId, dataId, fromVersion, toVersion));
    }
}
