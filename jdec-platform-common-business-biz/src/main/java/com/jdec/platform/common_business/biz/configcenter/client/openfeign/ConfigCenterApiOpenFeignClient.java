package com.jdec.platform.common_business.biz.configcenter.client.openfeign;

import com.jdec.platform.common_business.api.dto.response.*;
import com.jdec.platform.common_business.api.dto.response.MenuResp;
import com.jdec.platform.common_business.api.dto.response.UserTreeResp;
import com.jdec.platform.common_business.biz.dto.configcenter.requst.CheckUserSpecialPermissionReq;
import com.jdec.platform.common_business.biz.dto.configcenter.response.*;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "config-center",
        url = "${config.center.base}",
        path = "/api/config",
        configuration = FeignConfig.class)
public interface ConfigCenterApiOpenFeignClient {
    @GetMapping("/sys/role/get-user-roles")
    ConfigApiResponse<List<RoleResp>> getRoleListBySubjectAndUserId(
            @RequestHeader("X-Subject-Id") Long subject,
            @RequestParam Long subjectId,
            @RequestParam Long userId);

    @GetMapping("/sys/role/get-role-menu-by-category")
    ConfigApiResponse<List<MenuResp>> getMenuListByRoleId(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestParam Long roleId); // 去掉参数 @RequestParam Integer category

    @GetMapping("/modules/complete/{moduleId}")
    ConfigApiResponse<ModuleInfoResp> getModuleInfoComplete(
            @RequestHeader("X-Subject-Id") Long subjectId, @PathVariable Long moduleId);

    @GetMapping("/sys/module-field-permission/role-permissions")
    ConfigApiResponse<List<ModuleColumnPermissionResp>> getModuleFieldPermission(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestParam Long roleId,
            @RequestParam Long moduleId);

    @GetMapping("/sys/user/subjects/{userId}")
    ConfigApiResponse<List<SubjectResp>> getUserSubjectList(
            @RequestHeader("X-Subject-Id") Long subjectId, @PathVariable Long userId);

    // 获取模块树
    @GetMapping("/modules/tree")
    ConfigApiResponse<List<ModuleNodeResp>> getModuleNodeTree(
            @RequestHeader("X-Subject-Id") Long subjectId);

    // 获取模块列表
    @GetMapping("/modules")
    ConfigApiResponse<List<ModuleResp>> getModuleList(
            @RequestHeader("X-Subject-Id") Long subjectId, @RequestParam boolean availableOnly);

    @GetMapping("/sys/user/detail/{userId}")
    ConfigApiResponse<SysUserResp> getUserDetailByUserId(
            @RequestHeader("X-Subject-Id") Long subjectId, @PathVariable Long userId);

    // 获取用户在本系统是否有登录权限(有角色)
    @GetMapping("/sys/user/check-permission")
    ConfigApiResponse<UserCheckResp> getCurrentUserCheck(@RequestParam String phone);

    // 获取主体下的业务菜单列表
    @GetMapping("/menu/list")
    ConfigApiResponse<List<MenuListResp>> getMenuList(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestParam(required = false) Integer category);

    // 获取部门用户树
    @GetMapping("/sys/role/get-user-switch-tree")
    ConfigApiResponse<List<UserTreeResp>> getUserTreeByUserType(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestParam Integer userType,
            @RequestParam(required = false) String userName);

    @GetMapping("/sys/role/check-role-permission")
    ConfigApiResponse<Boolean> checkRolePermission(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestParam(required = false) Long roleId);

    // 获取模块交互权限配置
    @GetMapping("/sys/role/get-role-interaction-permission-by-module")
    ConfigApiResponse<ModuleInterOperateRightResp> getModuleInterOperateRight(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestParam Long moduleId,
            @RequestParam Long roleId);

    @PostMapping("/sys/special-permission/check")
    ConfigApiResponse<CheckUserSpecialPermissionResp> checkUserSpecialPermission(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestBody CheckUserSpecialPermissionReq req);

    // 根据角色ID获取数据权限配置
    @GetMapping("/sys/role/get-role-data-permission-detail")
    ConfigApiResponse<List<DataRightConfigResp>> getDataRightConfigItems(
            @RequestHeader("X-Subject-Id") Long subjectId, @RequestParam Long roleId);

    // 根据角色ID 获取用户的有权限的模块 列头
    @GetMapping("/sys/role/get-personal-setting-by-role")
    ConfigApiResponse<List<PersonalSettingResp>> getPersonalSettingByRole(
            @RequestHeader("X-Subject-Id") Long subjectId,
            @RequestParam Long roleId,
            @RequestParam(required = false) Long moduleId);

    // 根据角色ID 获取角色的配置详情
    @GetMapping("/sys/role/get-role-config-detail")
    ConfigApiResponse<RoleConfigDetailResp> getRoleConfigDetail(
            @RequestHeader("X-Subject-Id") Long subjectId, @RequestParam Long roleId);

    @GetMapping("/wechat/templates/{id}")
    ConfigApiResponse<WechatTemplateDetailResp> getWechatTemplateDetail(
            @RequestHeader("X-Subject-Id") Long subjectId, @PathVariable Long id);

    @GetMapping("/category/item/byCategoryAlias")
    ConfigApiResponse<List<SysConfigItemTreeResp>> getCategoryItemByCategoryAlias(
            @RequestHeader("X-Subject-Id") Long subjectId, @PathVariable String categoryAlias);

    @PostMapping("/wechat/templates/params/list-by-ids")
    ConfigApiResponse<List<WechatTemplateParamEntryResp>> getWechatTemplateParamEntryByIds(
            @RequestHeader("X-Subject-Id") Long subjectId, @RequestBody List<Long> ids);

    //    @PostMapping("/wechat/templates/send-message")
    //    ConfigApiResponse<Boolean> sendMessage(@RequestHeader("X-Subject-Id") Long
    // subjectId,@RequestBody SendWechatMessageReq req);
}
