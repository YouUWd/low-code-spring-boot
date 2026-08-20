package com.jdec.platform.config.biz.controller;

import cn.hutool.core.util.StrUtil;
import com.jdec.platform.config.api.SysScheduledTaskApi;
import com.jdec.platform.config.api.dto.request.CreateScheduledTaskReq;
import com.jdec.platform.config.api.dto.response.ScheduledTaskResp;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 定时 HTTP 任务 控制器 */
@Slf4j
@Tag(name = "定时任务管理", description = "定时 HTTP 调用任务的创建、查询与立即执行")
@RestController
@RequestMapping("/api/config/scheduled-tasks")
@RequiredArgsConstructor
public class SysScheduledTaskController {

    private final SysScheduledTaskApi scheduledTaskService;

    @Operation(summary = "创建定时任务", description = "供外部系统调用，创建一个定时 HTTP 任务，到达计划执行时间后自动调用指定地址")
    @PostMapping("/create")
    public ApiResponse<Long> create(@RequestBody CreateScheduledTaskReq req) {
        String projectNo =
                StrUtil.isNotBlank(req.getProjectNo())
                        ? req.getProjectNo()
                        : AppContext.getProjectNo();
        Long subjectId =
                req.getSubjectId() != null ? req.getSubjectId() : AppContext.getSubjectId();
        log.info(
                "创建定时任务: taskName={}, executeTime={}, requestUrl={}",
                req.getTaskName(),
                req.getExecuteTime(),
                req.getRequestUrl());
        Long taskId = scheduledTaskService.createTask(projectNo, subjectId, req);
        return ApiResponse.success("创建成功", taskId);
    }

    @Operation(summary = "查询任务详情")
    @GetMapping("/{id}")
    public ApiResponse<ScheduledTaskResp> getById(
            @Parameter(description = "任务ID", required = true) @PathVariable Long id) {
        return ApiResponse.success(scheduledTaskService.getTaskById(id));
    }

    @Operation(summary = "查询任务列表", description = "status 可传 0-新建, 1-执行成功, 2-执行失败，不传则查询全部")
    @GetMapping("/list")
    public ApiResponse<List<ScheduledTaskResp>> list(
            @Parameter(description = "任务状态") @RequestParam(required = false) Integer status) {
        return ApiResponse.success(scheduledTaskService.listTasksByStatus(status));
    }

    @Operation(summary = "立即执行任务", description = "跳过定时调度，立即执行一次该 HTTP 任务，用于测试")
    @PostMapping("/{id}/execute-now")
    public ApiResponse<Boolean> executeNow(
            @Parameter(description = "任务ID", required = true) @PathVariable Long id) {
        log.info("立即执行定时任务: id={}", id);
        scheduledTaskService.executeNow(id);
        return ApiResponse.success("已触发执行", true);
    }
}
