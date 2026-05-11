package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysStatusApi;
import com.jdec.platform.config.api.dto.request.SysStatusDragReq;
import com.jdec.platform.config.api.dto.request.SysStatusSaveReq;
import com.jdec.platform.config.api.dto.response.SysStatusTreeResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 状态配置 REST 控制器
 *
 * <p>提供状态的树形展示、增删改、拖拽接口。
 */
@RestController
@RequestMapping("/api/config/status")
@RequiredArgsConstructor
@Tag(name = "状态配置", description = "状态的树形展示、增删改、拖拽接口")
public class SysStatusController {

    private final SysStatusApi sysStatusApi;

    @GetMapping("/tree")
    @Operation(summary = "查询状态树", description = "查询所有状态的树形结构")
    public ApiResponse<List<SysStatusTreeResp>> tree() {
        return ApiResponse.success(sysStatusApi.tree());
    }

    @PostMapping("/save")
    @Operation(summary = "新增/编辑状态", description = "id为空则新增，不为空则修改")
    public ApiResponse<Void> save(@RequestBody SysStatusSaveReq request) {
        sysStatusApi.saveStatus(request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除状态", description = "删除指定状态及其所有子状态")
    public ApiResponse<Void> delete(
            @Parameter(description = "状态ID", example = "1") @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") Long subjectId) {
        sysStatusApi.deleteStatus(id, subjectId);
        return ApiResponse.success();
    }

    @PostMapping("/drag")
    @Operation(summary = "拖拽状态", description = "将状态拖拽到指定父状态下，拖到顶层时targetPid传0")
    public ApiResponse<Void> drag(@RequestBody SysStatusDragReq request) {
        sysStatusApi.dragStatus(request);
        return ApiResponse.success();
    }
}
