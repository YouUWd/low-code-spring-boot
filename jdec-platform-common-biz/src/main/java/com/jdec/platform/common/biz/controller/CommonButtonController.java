package com.jdec.platform.common.biz.controller;

import com.jdec.platform.config.api.dto.response.SysButtonResp;
import com.jdec.platform.config.open.api.OpenButtonApi;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
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
}
