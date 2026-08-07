package com.jdec.platform.common_business.biz.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.common_business.api.dto.response.MenuResp;
import com.jdec.platform.common_business.api.dto.response.RoleResp;
import com.jdec.platform.common_business.api.dto.response.SubjectResp;
import com.jdec.platform.common_business.api.dto.response.UserTreeResp;
import com.jdec.platform.common_business.biz.common.CommonConstant;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.ConfigCenterApiOpenFeignClient;
import com.jdec.platform.common_business.biz.dto.WechatTemplateDetailDTO;
import com.jdec.platform.common_business.biz.dto.configcenter.response.*;
import com.jdec.platform.common_business.biz.service.ConfigCenterApiService;
import com.jdec.platform.common_business.biz.util.ToolUtils;
import com.jdec.platform.config.api.dto.response.SysFieldResp;
import com.jdec.platform.config.api.dto.response.WechatTemplateDetailResp;
import com.jdec.platform.config.api.dto.response.WechatTemplateParamEntryResp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConfigCenterApiServiceImpl implements ConfigCenterApiService {

    private final ConfigCenterApiOpenFeignClient configCenterApiClient;

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

    @Override
    public WechatTemplateDetailDTO getWechatTemplateDetail(Long subjectId, Long id) {
        ConfigApiResponse<WechatTemplateDetailResp> configApiResponse =
                configCenterApiClient.getWechatTemplateDetail(subjectId, id);
        WechatTemplateDetailDTO wechatTemplateDetailDTO = new WechatTemplateDetailDTO();
        if (ConfigApiResponse.isSuccessResponse(configApiResponse)) {
            if (configApiResponse.getData() == null) {
                return wechatTemplateDetailDTO;
            }
            wechatTemplateDetailDTO.setWechatTemplateDetailResp(configApiResponse.getData());
            if (CollectionUtil.isNotEmpty(configApiResponse.getData().getTemplateAcceptType())) {
                // 查询接受人类型
                List<SysConfigItemTreeResp> sysConfigItemTreeRespList =
                        getCategoryItemByCategoryAlias(
                                subjectId,
                                CommonConstant.CONFIG_CENTER_CATEGORY_ALIAS_WX_ACCEPT_TYPE);
                if (CollectionUtil.isNotEmpty(sysConfigItemTreeRespList)) {
                    Map<Long, SysConfigItemTreeResp> sysConfigItemTreeRespMap =
                            sysConfigItemTreeRespList.stream()
                                    .collect(Collectors.toMap(x -> x.getId(), x -> x));
                    List<String> templateAcceptTypeList = new ArrayList<>();
                    for (Long x : configApiResponse.getData().getTemplateAcceptType()) {
                        SysConfigItemTreeResp treeResp = sysConfigItemTreeRespMap.get(x);
                        if (treeResp != null) {
                            templateAcceptTypeList.add(treeResp.getValue());
                        }
                    }
                    wechatTemplateDetailDTO.setTemplateAcceptTypeEnum(templateAcceptTypeList);
                }
            }
            if (CollectionUtil.isNotEmpty(configApiResponse.getData().getConfigParamIds())) {
                // 查询接受人类型
                List<WechatTemplateParamEntryResp> respList =
                        getWechatTemplateParamEntryByIds(
                                subjectId, configApiResponse.getData().getConfigParamIds());
                wechatTemplateDetailDTO.setTemplateParamEntryRespList(respList);
            }
            return wechatTemplateDetailDTO;
        }
        return null;
    }

    @Override
    public List<SysConfigItemTreeResp> getCategoryItemByCategoryAlias(
            Long subjectId, String categoryAlias) {
        ConfigApiResponse<List<SysConfigItemTreeResp>> configApiResponse =
                configCenterApiClient.getCategoryItemByCategoryAlias(subjectId, categoryAlias);
        if (ConfigApiResponse.isSuccessResponse(configApiResponse)) {
            return configApiResponse.getData();
        }
        return List.of();
    }

    @Override
    public List<WechatTemplateParamEntryResp> getWechatTemplateParamEntryByIds(
            Long subjectId, List<Long> ids) {
        ConfigApiResponse<List<WechatTemplateParamEntryResp>> configApiResponse =
                configCenterApiClient.getWechatTemplateParamEntryByIds(subjectId, ids);
        if (ConfigApiResponse.isSuccessResponse(configApiResponse)) {
            return configApiResponse.getData();
        }
        return List.of();
    }

    @Override
    public List<SysFieldResp> getColumnSettings(
            Long subjectId, String tableName, String columnName) {
        ConfigApiResponse<List<SysFieldResp>> configApiResponse =
                configCenterApiClient.getColumnSettings(subjectId, tableName, columnName);
        if (ConfigApiResponse.isSuccessResponse(configApiResponse)) {
            return configApiResponse.getData();
        }
        return List.of();
    }
}
