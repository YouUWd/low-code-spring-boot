package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.DataQueryApi;
import com.jdec.platform.config.api.dto.request.DataQueryReq;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
@Tag(name = "数据查询", description = "动态数据查询接口")
public class DataQueryController {

    private final DataQueryApi dataQueryApi;

    /**
     * 根据模块编码执行动态查询
     *
     * @param moduleId 模块Id
     * @param request 查询请求参数
     * @return 分页查询结果
     */
    @PostMapping("/{moduleId}")
    @Operation(summary = "执行动态查询", description = "根据模块配置动态构建SQL并执行查询，支持多种过滤操作符和多字段排序")
    public ApiResponse<PageResult<Map<String, Object>>> executeQuery(
            @PathVariable @Parameter(description = "模块编码", example = "MOD-STUDENT-LIST")
                    Long moduleId,
            @RequestBody DataQueryReq request) {

        PageResult<Map<String, Object>> result =
                dataQueryApi.executeQuery(
                        moduleId, AppContext.getProjectNo(), AppContext.getSubjectId(), request);
        return ApiResponse.success(result);
    }
}
