package com.jdec.platform.common.biz.controller;

import com.jdec.platform.config.api.dto.request.SysButtonMenuQueryReq;
import com.jdec.platform.config.api.dto.response.SysButtonMenuGroupResp;
import com.jdec.platform.config.api.dto.response.SysButtonResp;
import com.jdec.platform.config.open.api.OpenButtonApi;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 按钮列表控制器（公共模块） */
@Slf4j
@RestController("commonButtonController")
@RequestMapping("/api/common/button")
@RequiredArgsConstructor
@Tag(name = "按钮列表接口", description = "查询系统配置中的按钮列表")
public class CommonButtonController {

    private final OpenButtonApi openButtonApi;

    @GetMapping("/list")
    @Operation(summary = "查询按钮列表", description = "返回当前项目 + 主体下系统配置的按钮列表，按排序值升序，不分页")
    public ApiResponse<List<SysButtonResp>> list() {
        try {
            return ApiResponse.success(openButtonApi.listButtons());
        } catch (Exception ex) {
            log.error("Common button list failed: err={}", ex.getMessage(), ex);
            throw ex;
        }
    }

    @PostMapping("/list/by-menu")
    @Operation(
            summary = "按菜单+类型查询按钮",
            description = "根据菜单ID和按钮类型查询按钮信息并按菜单分组返回；支持菜单ID为空、仅按类型查询（对象数组仅一个元素、menuId为空）的场景")
    public ApiResponse<List<SysButtonMenuGroupResp>> listByMenu(
            @RequestBody List<SysButtonMenuQueryReq> req) {
        try {
            return ApiResponse.success(openButtonApi.queryButtonsByMenu(req));
        } catch (Exception ex) {
            log.error("Common button list by menu failed: err={}", ex.getMessage(), ex);
            throw ex;
        }
    }
}
