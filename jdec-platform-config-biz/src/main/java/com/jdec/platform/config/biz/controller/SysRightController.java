package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysRightApi;
import com.jdec.platform.config.api.dto.request.QuerySysRightReq;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 权限节点管理 REST 控制器 */
@RestController
@RequestMapping("/api/config/sys/right")
@RequiredArgsConstructor
@Tag(name = "权限节点管理", description = "权限节点的增删改查接口")
public class SysRightController {

    private final SysRightApi sysRightApi;

    @GetMapping("/list")
    @Operation(summary = "查询权限节点列表", description = "支持权限节点名称过滤")
    public ApiResponse<PageResult<SysRightResp>> listRight(QuerySysRightReq request) {
        return ApiResponse.success(sysRightApi.listRight(request));
    }
}
