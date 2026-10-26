package com.jdec.platform.config.biz.controller;

import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.third.ProjectApiClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目管理外部服务 REST 控制器
 *
 * <p>透传项目外部 API 的调用，提供项目信息查询接口。
 */
@RestController
@RequestMapping("/api/config/project")
@RequiredArgsConstructor
@Tag(name = "项目管理外部服务", description = "项目外部 API 的信息查询接口")
public class ProjectApiController {

    private final ProjectApiClient projectApiClient;

    @PostMapping("/info")
    @Operation(summary = "根据业务编号查询项目信息")
    public ApiResponse<Map<String, Object>> getProjectInfoByNo() {
        return ApiResponse.success(projectApiClient.getProjectInfo());
    }
}
