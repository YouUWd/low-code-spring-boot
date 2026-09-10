package com.jdec.platform.dataengine.biz.controller;

import com.jdec.platform.dataengine.api.DataEngineApi;
import com.jdec.platform.dataengine.api.dto.request.DynamicQueryReq;
import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.dataengine.api.dto.response.DynamicQueryResp;
import com.jdec.platform.shared.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/data-engine/v1/modules")
@RequiredArgsConstructor
public class DataEngineController {

    private final DataEngineApi dataEngineApi;

    @PostMapping("/{moduleId}/query")
    public ApiResponse<DynamicQueryResp> query(
            @PathVariable Long moduleId, @RequestBody DynamicQueryReq req) {
        req.setModuleId(moduleId);
        return ApiResponse.success(dataEngineApi.query(req));
    }

    @GetMapping("/{moduleId}/detail/{id}")
    public ApiResponse<List<Object>> getDetail(
            @PathVariable Long moduleId, @PathVariable Long id, String btnType) {
        return ApiResponse.success(dataEngineApi.getDetail(moduleId, id, btnType));
    }

    @PostMapping("/{moduleId}/save")
    public ApiResponse<Long> save(@PathVariable Long moduleId, @RequestBody DynamicSaveReq req) {
        return ApiResponse.success(dataEngineApi.save(req));
    }
}
