package com.jdec.platform.common_business.biz.configcenter.client;

import com.jdec.platform.common_business.api.dto.response.MenuListResp;
import com.jdec.platform.common_business.api.dto.response.RoleResp;
import com.jdec.platform.common_business.api.dto.response.SubjectResp;
import com.jdec.platform.common_business.biz.dto.configcenter.requst.CheckUserSpecialPermissionReq;
import com.jdec.platform.common_business.biz.dto.configcenter.response.*;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface ConfigCenterApiClient {

    @GetExchange("/sys/role/get-user-roles")
    public ConfigApiResponse<List<RoleResp>> getRoleListBySubjectAndUserId(
            @RequestHeader("X-Subject-Id") Long subject,
            @RequestParam Long subjectId,
            @RequestParam Long userId);

    @GetExchange("/sys/role/get-role-menu-by-category")
    public ConfigApiResponse<List<MenuResp>> getMenuListByRoleId(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestParam Long roleId); // 去掉参数 @RequestParam Integer category

    @GetExchange("/modules/complete/{moduleId}")
    public ConfigApiResponse<ModuleInfoResp> getModuleInfoComplete(
            @RequestHeader("X-Subject-Id") Long subjectId, @PathVariable Long moduleId);

    @GetExchange("/sys/module-field-permission/role-permissions")
    public ConfigApiResponse<List<ModuleColumnPermissionResp>> getModuleFieldPermission(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestParam Long roleId,
            @RequestParam Long moduleId);

    @GetExchange("/sys/user/subjects/{userId}")
    public ConfigApiResponse<List<SubjectResp>> getUserSubjectList(
            @RequestHeader("X-Subject-Id") Long subjectId, @PathVariable Long userId);

    // 获取模块树
    @GetExchange("/modules/tree")
    ConfigApiResponse<List<ModuleNodeResp>> getModuleNodeTree(
            @RequestHeader("X-Subject-Id") Long subjectId);

    // 获取模块列表
    @GetExchange("/modules")
    ConfigApiResponse<List<ModuleResp>> getModuleList(
            @RequestHeader("X-Subject-Id") Long subjectId, @RequestParam boolean availableOnly);

    @GetExchange("/sys/user/detail/{userId}")
    ConfigApiResponse<SysUserResp> getUserDetailByUserId(
            @RequestHeader("X-Subject-Id") Long subjectId, @PathVariable Long userId);

    // 获取用户在本系统是否有登录权限(有角色)
    @GetExchange("/sys/user/check-permission")
    ConfigApiResponse<UserCheckResp> getCurrentUserCheck(@RequestParam String phone);

    // 获取主体下的业务菜单列表
    @GetExchange("/menu/list")
    ConfigApiResponse<List<MenuListResp>> getMenuList(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestParam(required = false) Integer category);

    // 获取部门用户树
    @GetExchange("/sys/role/get-user-switch-tree")
    ConfigApiResponse<List<UserTreeResp>> getUserTreeByUserType(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestParam Integer userType,
            @RequestParam(required = false) String userName);

    @GetExchange("/sys/role/check-role-permission")
    ConfigApiResponse<Boolean> checkRolePermission(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestParam(required = false) Long roleId);

    // 获取模块交互权限配置
    @GetExchange("/sys/role/get-role-interaction-permission-by-module")
    ConfigApiResponse<ModuleInterOperateRightResp> getModuleInterOperateRight(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestParam Long moduleId,
            @RequestParam Long roleId);

    @PostExchange("/sys/special-permission/check")
    ConfigApiResponse<CheckUserSpecialPermissionResp> checkUserSpecialPermission(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestBody CheckUserSpecialPermissionReq req);

    // 根据角色ID获取数据权限配置
    @GetExchange("/sys/role/get-role-data-permission-detail")
    ConfigApiResponse<List<DataRightConfigResp>> getDataRightConfigItems(
            @RequestHeader("X-Subject-Id") Long subjectId, @RequestParam Long roleId);

    // 根据角色ID 获取用户的有权限的模块 列头
    @GetExchange("/sys/role/get-personal-setting-by-role")
    ConfigApiResponse<List<PersonalSettingResp>> getPersonalSettingByRole(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestParam Long roleId,
            @RequestParam(required = false) Long moduleId);
}
