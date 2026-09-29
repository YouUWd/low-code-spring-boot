package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.SysSearchConfigApi;
import com.jdec.platform.config.api.dto.response.SearchBusinessTypeOptionResp;
import com.jdec.platform.config.api.dto.response.SearchConfigItemResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 下拉搜索配置控制器 */
@Slf4j
@Tag(name = "下拉搜索配置", description = "按字段类型查询可用的搜索条件配置")
@RestController
@RequestMapping("/api/config/search-config")
@RequiredArgsConstructor
public class SysSearchConfigController {

    private final SysSearchConfigApi sysSearchConfigApi;

    @Operation(
            summary = "按字段索引查询搜索配置",
            description =
                    "字段索引形如 1001.info.company_name，后端拆出表名/字段名后查 information_schema 得到数据类型，"
                            + "再与 search_config.json 的 dataType 匹配，返回对应的 searchConfig")
    @GetMapping
    public ApiResponse<List<SearchConfigItemResp>> listSearchConfig(
            @Parameter(
                            description = "字段索引（模块ID.表名.字段名）",
                            example = "1001.info.company_name",
                            required = true)
                    @RequestParam
                    String dataIndex) {
        log.info("查询字段索引 {} 的搜索配置", dataIndex);
        return ApiResponse.success(sysSearchConfigApi.listSearchConfig(dataIndex));
    }

    @Operation(
            summary = "查询业务类型下拉选项",
            description =
                    "返回 search_config.json 中的 businessType / businessTypeName，已按 businessType 去重，供前端下拉框展示")
    @GetMapping("/business-type")
    public ApiResponse<List<SearchBusinessTypeOptionResp>> listBusinessTypeOptions() {
        log.info("查询业务类型下拉选项");
        return ApiResponse.success(sysSearchConfigApi.listBusinessTypeOptions());
    }
}
