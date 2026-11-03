package com.jdec.platform.config.biz.controller;

import com.jdec.platform.config.api.dto.request.ProjectInfoReq;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.third.ProjectApiClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目管理外部服务 REST 控制器
 *
 * <p>透传项目外部 API 的调用，提供项目信息查询接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/config/project")
@RequiredArgsConstructor
@Tag(name = "项目管理外部服务", description = "项目外部 API 的信息查询接口")
public class ProjectApiController {

    private final ProjectApiClient projectApiClient;

    @PostMapping("/info")
    @Operation(
            summary = "根据业务编号查询项目信息",
            description = "根据业务编号查询外部项目系统的基础信息(如名称、LOGO、图标等)，未传业务编号时自动使用当前项目上下文或系统配置默认编号")
    public ApiResponse<Map<String, Object>> getProjectInfoByNo(
            @RequestBody(required = false) ProjectInfoReq req) {
        String businessNo =
                (req != null && StringUtils.hasText(req.getBusinessNo()))
                        ? req.getBusinessNo().trim()
                        : AppContext.getProjectNo();
        List<String> columns =
                (req != null && !CollectionUtils.isEmpty(req.getColumns()))
                        ? req.getColumns()
                        : null;
        try {
            Map<String, Object> projectInfo =
                    projectApiClient.getProjectInfoByNo(businessNo, columns);
            return ApiResponse.success(projectInfo);
        } catch (Exception ex) {
            log.error(
                    "查询外部项目信息失败: businessNo={}, columns={}, err={}",
                    businessNo,
                    columns,
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }
}
