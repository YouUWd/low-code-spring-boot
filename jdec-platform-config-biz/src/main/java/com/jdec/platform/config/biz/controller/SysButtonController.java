package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysButtonApi;
import com.jdec.platform.config.api.dto.request.SysButtonMenuQueryReq;
import com.jdec.platform.config.api.dto.request.SysButtonPageReq;
import com.jdec.platform.config.api.dto.request.SysButtonSaveReq;
import com.jdec.platform.config.api.dto.response.SysButtonMenuGroupResp;
import com.jdec.platform.config.api.dto.response.SysButtonResp;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 按钮配置 REST 控制器
 *
 * <p>提供按钮的分页查询、列表查询、增删改接口。
 */
@RestController
@RequestMapping("/api/config/button")
@RequiredArgsConstructor
@Tag(name = "按钮配置", description = "按钮的分页查询、列表查询、增删改接口")
public class SysButtonController {

    private final SysButtonApi sysButtonApi;

    @GetMapping("/page")
    @Operation(summary = "分页查询按钮列表")
    public ApiResponse<PageResult<SysButtonResp>> page(SysButtonPageReq req) {
        return ApiResponse.success(sysButtonApi.page(req));
    }

    @GetMapping("/list")
    @Operation(summary = "查询所有按钮（不分页）")
    public ApiResponse<List<SysButtonResp>> list() {
        return ApiResponse.success(sysButtonApi.list());
    }

    @PostMapping("/list/by-menu")
    @Operation(
            summary = "按菜单+类型查询按钮",
            description = "根据菜单ID和按钮类型查询已启用的按钮（仅返回前端需要的字段），并按菜单分组返回；支持菜单ID为空、仅按类型查询的场景")
    public ApiResponse<List<SysButtonMenuGroupResp>> listByMenu(
            @RequestBody List<SysButtonMenuQueryReq> req) {
        return ApiResponse.success(sysButtonApi.queryButtonsByMenu(req));
    }

    @PostMapping("/save")
    @Operation(summary = "新增/编辑按钮", description = "id为空则新增，不为空则修改")
    public ApiResponse<Void> save(@RequestBody SysButtonSaveReq request) {
        sysButtonApi.saveButton(request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除按钮")
    public ApiResponse<Void> delete(
            @Parameter(description = "按钮ID", example = "1") @PathVariable Long id) {
        sysButtonApi.deleteButton(id);
        return ApiResponse.success();
    }
}
