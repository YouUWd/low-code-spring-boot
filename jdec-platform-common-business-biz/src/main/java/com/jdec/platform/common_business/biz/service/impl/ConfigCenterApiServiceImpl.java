package com.jdec.platform.common_business.biz.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.common_business.api.dto.response.RoleResp;
import com.jdec.platform.common_business.api.dto.response.SubjectResp;
import com.jdec.platform.common_business.biz.configcenter.client.ConfigCenterApiClient;
import com.jdec.platform.common_business.biz.dto.configcenter.response.*;
import com.jdec.platform.common_business.biz.service.ConfigCenterApiService;
import com.jdec.platform.common_business.biz.util.ToolUtils;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConfigCenterApiServiceImpl implements ConfigCenterApiService {

    private final ConfigCenterApiClient configCenterApiClient;

    @Override
    public List<RoleResp> getRoleListBySubjectAndUserId(Long subjectId, Long userId) {
        ConfigApiResponse<List<RoleResp>> response =
                configCenterApiClient.getRoleListBySubjectAndUserId(subjectId, subjectId, userId);
        if (ConfigApiResponse.isSuccessResponse(response)) {
            return response.getData();
        }
        return List.of();
    }

    @Override
    public List<MenuResp> getMenuListByRoleId(Long subjectId, Long roleId, Integer category) {
        ConfigApiResponse<List<MenuResp>> response =
                configCenterApiClient.getMenuListByRoleId(subjectId, roleId);
        if (!ConfigApiResponse.isSuccessResponse(response)) {
            return List.of();
        }
        List<MenuResp> menuRespList = response.getData();
        if (CollectionUtil.isEmpty(menuRespList)) {
            return List.of();
        }
        menuRespList.sort(Comparator.comparing(MenuResp::getSortOrder));
        return ToolUtils.buildTree(menuRespList, 0L, "menuId", "pid");
    }

    @Override
    public ModuleInfoResp getModuleInfoComplete(Long subjectId, Long moduleId) {
        ConfigApiResponse<ModuleInfoResp> response =
                configCenterApiClient.getModuleInfoComplete(subjectId, moduleId);

        if (ConfigApiResponse.isSuccessResponse(response)) {
            return response.getData();
        }
        return null;
    }

    @Override
    public List<ModuleColumnPermissionResp> getModuleFieldPermission(
            Long subjectId, Long roleId, Long moduleId) {
        ConfigApiResponse<List<ModuleColumnPermissionResp>> response =
                configCenterApiClient.getModuleFieldPermission(subjectId, roleId, moduleId);
        if (ConfigApiResponse.isSuccessResponse(response)) {
            return response.getData();
        }
        return null;
    }

    @Override
    public List<SubjectResp> getUserSubjectList(Long subjectId, Long userId) {
        ConfigApiResponse<List<SubjectResp>> response =
                configCenterApiClient.getUserSubjectList(subjectId, userId);
        if (ConfigApiResponse.isSuccessResponse(response)) {
            return response.getData();
        }
        return null;
    }

    @Override
    public List<ModuleNodeResp> getModuleNodeTree(Long subjectId) {
        ConfigApiResponse<List<ModuleNodeResp>> response =
                configCenterApiClient.getModuleNodeTree(subjectId);
        if (ConfigApiResponse.isSuccessResponse(response)) {
            return response.getData();
        }
        return null;
    }

    @Override
    public List<ModuleResp> getModuleList(Long subjectId, boolean availableOnly) {
        ConfigApiResponse<List<ModuleResp>> response =
                configCenterApiClient.getModuleList(subjectId, true);
        if (ConfigApiResponse.isSuccessResponse(response)) {
            return response.getData();
        }
        return null;
    }

    @Override
    public SysUserResp getUserDetailByUserId(Long subjectId, Long userId) {
        ConfigApiResponse<SysUserResp> response =
                configCenterApiClient.getUserDetailByUserId(subjectId, userId);
        if (ConfigApiResponse.isSuccessResponse(response)) {
            return response.getData();
        }
        return null;
    }

    @Override
    public List<UserTreeResp> getUserTreeByUserType(
            Long subjectId, Integer userType, String userName) {
        ConfigApiResponse<List<UserTreeResp>> userTreeResponse =
                configCenterApiClient.getUserTreeByUserType(subjectId, userType, userName);
        if (ConfigApiResponse.isSuccessResponse(userTreeResponse)) {
            return userTreeResponse.getData();
        }
        return List.of();
    }

    @Override
    public ModuleInterOperateRightResp getModuleInterOperateRights(
            Long subjectId, Long roleId, Long moduleId) {
        ConfigApiResponse<ModuleInterOperateRightResp> moduleInterOpRight =
                configCenterApiClient.getModuleInterOperateRight(subjectId, moduleId, roleId);
        if (ConfigApiResponse.isSuccessResponse(moduleInterOpRight)) {
            return moduleInterOpRight.getData();
        }
        return null;
    }

    @Override
    public List<DataRightConfigResp> getDataRightConfigItem(Long subjectId, Long roleId) {
        ConfigApiResponse<List<DataRightConfigResp>> configItems =
                configCenterApiClient.getDataRightConfigItems(subjectId, roleId);
        if (ConfigApiResponse.isSuccessResponse(configItems)) {
            return configItems.getData();
        }
        return List.of();
    }

    @Override
    public List<PersonalSettingResp> getPersonalSettingByRole(
            Long subjectId, Long roleId, Long moduleId) {
        ConfigApiResponse<List<PersonalSettingResp>> configItems =
                configCenterApiClient.getPersonalSettingByRole(subjectId, roleId, moduleId);
        if (ConfigApiResponse.isSuccessResponse(configItems)) {
            return configItems.getData();
        }
        return List.of();
    }
}
