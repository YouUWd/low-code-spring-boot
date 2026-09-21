package com.jdec.platform.config.biz.controller;

import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 按钮配置 REST 控制器
 *
 * <p>提供按钮的分页查询、列表查询、增删改接口。
 */
@RestController
@RequestMapping("/api/monitorlog")
@RequiredArgsConstructor
@Tag(name = "日志接口", description = "记录日志")
public class MonitorController {

    @PostMapping("/sendSystemNavLog")
    @Operation(summary = "发送系统菜单日志")
    public ApiResponse<Void> sendSystemNavLog() {
        return ApiResponse.customResponse(200, "模拟接口，没有真实实现", null);
    }

    @PostMapping("/sendSystemLog")
    @Operation(summary = "发送系统菜单日志")
    public ApiResponse<Void> sendSystemLog() {
        return ApiResponse.customResponse(200, "模拟接口，没有真实实现", null);
    }
}
