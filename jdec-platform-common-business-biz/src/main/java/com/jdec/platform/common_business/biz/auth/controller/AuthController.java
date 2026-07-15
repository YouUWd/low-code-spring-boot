package com.jdec.platform.common_business.biz.auth.controller;

import com.jdec.platform.common_business.biz.auth.dto.resp.CurrentUserCheckResp;
import com.jdec.platform.common_business.biz.auth.dto.resp.LoginResult;
import com.jdec.platform.common_business.biz.auth.service.AuthApi;
import com.jdec.platform.common_business.biz.context.TokenContextHolder;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController(value = "comBizAuthController")
@RequestMapping("/api/student")
@RequiredArgsConstructor
@Tag(
        name = "用户登录相关", // 分组名称（文档中显示的分组标签）
        description = "处理用户登录请求、判断用户是否有当前登录系统的权限" // 分组描述
        )
public class AuthController {

    private final AuthApi authService;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户登录")
    public ApiResponse<LoginResult> login(
            @Schema(description = "用户token，在header中传递。")
                    @RequestHeader(name = "Authorization", required = false)
                    String token) {
        try {
            TokenContextHolder.setCurrentUserToken(authService.extractToken(token));
            return ApiResponse.success(authService.login(token));
        } finally {
            TokenContextHolder.clear();
        }
    }

    @PostMapping("/permission/check-current-user")
    @Operation(summary = "检查当前用户在本系统是否有角色", description = "检查当前用户在本系统是否有角色")
    public ApiResponse<CurrentUserCheckResp> checkCurrentUser(
            @RequestHeader("JWT-TOKEN") String token) {
        try {
            TokenContextHolder.setCurrentUserToken(token);
            return ApiResponse.success(authService.getCurrentUserCheckInfo(token));
        } finally {
            TokenContextHolder.clear();
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "用户退出登录", description = "用户退出登录")
    public ApiResponse<Boolean> logout(
            @Schema(description = "用户token，在header中传递。")
                    @RequestHeader(name = "Authorization", required = false)
                    String token) {
        return ApiResponse.success(authService.logout(token));
    }
}
