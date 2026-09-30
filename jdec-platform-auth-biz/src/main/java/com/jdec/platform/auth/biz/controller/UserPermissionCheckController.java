package com.jdec.platform.auth.biz.controller;

import com.jdec.platform.auth.api.AuthApi;
import com.jdec.platform.auth.api.dto.response.CurrentUserCheckResp;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.TokenContext;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户系统权限预检 REST 控制器
 *
 * <p>处理用户在指定业务系统中的准入与角色权限预检，非全局权限管理。
 */
@RestController
@RequestMapping("/api/permission")
@RequiredArgsConstructor
@Tag(name = "用户系统准入检查", description = "检查当前用户在指定系统中的准入与角色权限")
public class UserPermissionCheckController {

    private final AuthApi authService;

    @PostMapping("/{projectNo}/check-current-user")
    @Operation(summary = "检查当前用户在指定系统是否有角色", description = "检查当前用户在指定系统是否有角色")
    public ApiResponse<CurrentUserCheckResp> checkCurrentUser(
            @Parameter(description = "项目编号", required = true) @PathVariable("projectNo")
                    String projectNo,
            @RequestHeader("JWT-TOKEN") String token) {
        try {
            TokenContext.setToken(token);
            AppContext.setContext(projectNo);
            return ApiResponse.success(authService.getCurrentUserCheckInfo(token));
        } finally {
            TokenContext.clear();
            AppContext.remove();
        }
    }
}
