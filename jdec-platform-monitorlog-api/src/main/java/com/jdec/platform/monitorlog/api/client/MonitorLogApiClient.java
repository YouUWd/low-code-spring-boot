package com.jdec.platform.monitorlog.api.client;

import com.jdec.platform.config.api.dto.response.RoleConfigDetailResp;
import com.jdec.platform.config.api.dto.response.UserDetailResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** 监控中心API客户端 @HttpExchange：标记在接口上，表示这是一个声明式 HTTP 客户端接口 */
@HttpExchange
public interface MonitorLogApiClient {

    @Operation(
            summary = "根据用户ID获取用户详情",
            description =
                    "检查逻辑：1. 先查sys_user表 2. 未查到则调用人力UserApi 3. 校验权限（系统设置或主体设置），无权限则抛出异常 4. 返回用户详情")
    @GetExchange("/api/config/sys/user/detail/{userId}") // 标记在方法上，声明该方法对应一个 HTTP GET 请求，并指定路径
    ApiResponse<UserDetailResp> getUserDetailByUserId(
            @Parameter(description = "用户ID", required = true, example = "1") @PathVariable
                    Long userId);

    @Operation(summary = "获取角色配置详情", description = "获取角色及其关联的用户、主体列表")
    @GetExchange("/api/config/sys/role/get-role-config-detail")
    ApiResponse<RoleConfigDetailResp> getRoleConfigDetail(
            @Parameter(description = "角色ID", example = "1") @RequestParam Long roleId);
}
