package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysBusinessTypeApi;
import com.jdec.platform.config.api.dto.response.BusinessTypeOptionResp;
import com.jdec.platform.config.api.dto.response.SearchConfigItemResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 业务类型控制器 */
@Slf4j
@Tag(name = "业务类型", description = "业务类型下拉及搜索配置查询")
@RestController
@RequestMapping("/api/config/business-type")
@RequiredArgsConstructor
public class SysBusinessTypeController {

    private final SysBusinessTypeApi sysBusinessTypeApi;

    @Operation(summary = "查询业务类型下拉列表", description = "返回全部未删除的业务类型，按ID升序")
    @GetMapping("/options")
    public ApiResponse<List<BusinessTypeOptionResp>> listBusinessTypeOptions() {
        log.info("查询业务类型下拉列表");
        return ApiResponse.success(sysBusinessTypeApi.listBusinessTypeOptions());
    }

    @Operation(
            summary = "根据业务类型查询搜索配置项",
            description = "返回 sys_business_type.search_type 中配置的搜索类型集合；业务类型为空或不存在时返回空集合")
    @GetMapping("/search-config")
    public ApiResponse<List<SearchConfigItemResp>> listSearchConfigByBusinessType(
            @Parameter(description = "业务类型标识", example = "descriptionText")
                    @RequestParam(required = false)
                    String businessTypeCode) {
        log.info("查询业务类型 {} 的搜索配置项", businessTypeCode);
        return ApiResponse.success(
                sysBusinessTypeApi.listSearchConfigByBusinessType(businessTypeCode));
    }
}
