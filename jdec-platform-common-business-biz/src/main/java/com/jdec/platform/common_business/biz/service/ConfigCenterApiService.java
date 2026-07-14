package com.jdec.platform.common_business.biz.service;

import com.jdec.platform.common_business.api.dto.response.RoleResp;
import com.jdec.platform.common_business.api.dto.response.SubjectResp;
import com.jdec.platform.common_business.biz.dto.configcenter.resp.*;
import java.util.List;

public interface ConfigCenterApiService {

    List<RoleResp> getRoleListBySubjectAndUserId(Long subjectId, Long userId);

    List<MenuResp> getMenuListByRoleId(Long subjectId, Long roleId, Integer category);

    ModuleInfoResp getModuleInfoComplete(Long subjectId, Long moduleId);

    List<ModuleColumnPermissionResp> getModuleFieldPermission(
            Long subjectId, Long roleId, Long moduleId);

    List<SubjectResp> getUserSubjectList(Long subjectId, Long userId);

    List<ModuleNodeResp> getModuleNodeTree(Long subjectId);

    List<ModuleResp> getModuleList(Long subjectId, boolean availableOnly);

    SysUserResp getUserDetailByUserId(Long subjectId, Long userId);

    List<UserTreeResp> getUserTreeByUserType(Long subjectId, Integer userType, String userName);

    ModuleInterOperateRightResp getModuleInterOperateRights(
            Long subjectId, Long roleId, Long moduleId);

    List<DataRightConfigResp> getDataRightConfigItem(Long subjectId, Long roleId);

    List<PersonalSettingResp> getPersonalSettingByRole(Long subjectId, Long roleId, Long moduleId);
}
