package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysStatusApi;
import com.jdec.platform.config.api.dto.request.SysStatusDragReq;
import com.jdec.platform.config.api.dto.request.SysStatusSaveReq;
import com.jdec.platform.config.api.dto.request.SysStatusToggleReq;
import com.jdec.platform.config.api.dto.response.SysStatusTreeResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    @Operation(summary = "新增/编辑状态", description = "id为空则新增，不为空则修改；新增时返回新记录ID")
    public ApiResponse<Long> save(@RequestBody SysStatusSaveReq request) {
        return ApiResponse.success(sysStatusApi.saveStatus(request));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "删除状态",
            description = "删除指定状态。force=false 时若有子状态则返回提示；force=true 时级联删除所有子状态")
    public ApiResponse<Void> delete(
            @Parameter(description = "状态ID", example = "1") @PathVariable Long id,
            @Parameter(description = "是否强制删除", example = "false")
                    @RequestParam(defaultValue = "false")
                    boolean force) {
        sysStatusApi.deleteStatus(id, force);
        return ApiResponse.success();
    }

    @PostMapping("/drag")
    @Operation(summary = "拖拽状态", description = "将状态拖拽到指定父状态下，拖到顶层时targetPid传0")
    public ApiResponse<Void> drag(@RequestBody SysStatusDragReq request) {
        sysStatusApi.dragStatus(request);
        return ApiResponse.success();
    }

    @PostMapping("/toggle")
    @Operation(summary = "停用/启用状态", description = "切换状态的启用状态，enabled=1启用，enabled=0停用")
    public ApiResponse<Void> toggle(@RequestBody SysStatusToggleReq request) {
        sysStatusApi.toggleStatus(request);
        return ApiResponse.success();
    }
}
