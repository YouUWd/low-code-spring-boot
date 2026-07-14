package com.jdec.platform.common_business.biz.controller;

import com.jdec.platform.common_business.api.SystemManageServiceApi;
import com.jdec.platform.common_business.api.dto.request.SelectRoleReq;
import com.jdec.platform.common_business.api.dto.response.MenuResp;
import com.jdec.platform.common_business.api.dto.response.RoleResp;
import com.jdec.platform.common_business.api.dto.response.SelectRoleResp;
import com.jdec.platform.common_business.api.dto.response.SubjectResp;
import com.jdec.platform.common_business.biz.common.CommonConstant;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ModuleColumnPermissionResp;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ModuleInfoResp;
import com.jdec.platform.common_business.biz.service.ConfigCenterApiService;
import com.jdec.platform.common_business.biz.util.LoginUserUtil;
import com.jdec.platform.common_business.biz.websocket.ClientSession;
import com.jdec.platform.common_business.biz.websocket.UserSessionHolder;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/student/config")
@Tag(
        name = "系统配置", // 分组名称（文档中显示的分组标签）
        description = "系统配置" // 分组描述
        )
public class SysConfigController {

    private final ConfigCenterApiService configCenterApiService;

    private final SystemManageServiceApi systemManageService;

    @GetMapping("/get/role-list")
    @Operation(summary = "获取角色列表", description = "获取角色列表")
    public ApiResponse<List<RoleResp>> getRoleListBySubjectAndUserId(
            @RequestHeader("Subject-Id") Long subjectId, @RequestParam Long userId) {
        // Mock-User-Id Subject-Id  Role-Id
        return ApiResponse.success(
                configCenterApiService.getRoleListBySubjectAndUserId(subjectId, userId));
    }

    @GetMapping("/get/get-role-menu-by-category")
    @Operation(summary = "获取菜单", description = "获取菜单")
    public ApiResponse<List<MenuResp>> getMenuListByRoleId(
            @RequestHeader("Subject-Id") Long subjectId, @RequestParam Long roleId) {
        // Mock-User-Id Subject-Id  Role-Id
        return ApiResponse.success(
                configCenterApiService.getMenuListByRoleId(
                        subjectId, roleId, CommonConstant.CONFIG_DEFAULT_MENU_CATEGORY));
    }

    @GetMapping("/get/module-complete")
    @Operation(summary = "获取模块信息", description = "获取模块信息")
    public ApiResponse<ModuleInfoResp> getModuleInfo(
            @RequestHeader("Subject-Id") Long subjectId, @RequestParam Long moduleId) {
        // Mock-User-Id Subject-Id  Role-Id
        return ApiResponse.success(
                configCenterApiService.getModuleInfoComplete(subjectId, moduleId));
    }

    @GetMapping("/get/module-field-permission")
    @Operation(summary = "获取模块信息", description = "获取模块信息")
    public ApiResponse<List<ModuleColumnPermissionResp>> getModuleFieldPermission(
            @RequestHeader("Subject-Id") Long subjectId,
            @RequestParam Long roleId,
            @RequestParam Long moduleId) {
        // Mock-User-Id Subject-Id  Role-Id
        return ApiResponse.success(
                configCenterApiService.getModuleFieldPermission(subjectId, roleId, moduleId));
    }

    @GetMapping("/get/role/subjects")
    @Operation(summary = "获取用户的主题列表", description = "获取用户的主题列表")
    public ApiResponse<List<SubjectResp>> getUserSubjectList(@RequestParam Long userId) {
        // Mock-User-Id Subject-Id  Role-Id
        return ApiResponse.success(
                configCenterApiService.getUserSubjectList(
                        LoginUserUtil.getLoginSubjectId(), userId));
    }

    @PostMapping("/select/role")
    @Operation(summary = "切换角色列表", description = "切换角色列表")
    public ApiResponse<SelectRoleResp> selectRole(@RequestBody SelectRoleReq req) {
        if (req == null) {
            req = new SelectRoleReq();
        }
        if (req.getSubjectId() == null) {
            Long subjectId = LoginUserUtil.getSubjectIdFromHeader();
            if (subjectId != null && !subjectId.equals(0L)) {
                req.setSubjectId(subjectId);
            }
        }
        if (req.getRoleId() == null) {
            Long roleId = LoginUserUtil.getRoleId();
            if (roleId != null && !roleId.equals(0L)) {
                req.setRoleId(roleId);
            }
        }
        // Mock-User-Id Subject-Id  Role-Id
        return systemManageService.selectRole(req);
    }

    @PostMapping("/get/connection")
    @Operation(summary = "获取系统连接监控", description = "获取系统连接监控")
    public ApiResponse getConnectionMonitor() {
        // Mock-User-Id Subject-Id  Role-Id
        List<ClientSession> clientSessionList = UserSessionHolder.getAllClientSession();
        Map<Long, Map<String, Map<Object, Object>>> result = new LinkedHashMap<>();
        for (ClientSession session : clientSessionList) {
            if (!result.containsKey(session.getUserId())) {
                result.put(session.getUserId(), new HashMap<>());
            }
            Map<Object, Object> v = new HashMap<>();
            v.put("userId", session.getUserId());
            v.put("subjectId", session.getSubjectId());
            v.put("roleId", session.getRoleId());
            v.put("mockUserId", session.getMockUserId());
            v.put("mockSubjectId", session.getMockSubjectId());
            v.put("mockRoleId", session.getMockRoleId());
            result.get(session.getUserId()).put(session.getWebSocketSession().getId(), v);
        }
        return ApiResponse.success(result);
    }
}
