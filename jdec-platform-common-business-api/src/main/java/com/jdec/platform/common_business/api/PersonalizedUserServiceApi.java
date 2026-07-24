package com.jdec.platform.common_business.api;

import com.jdec.platform.common_business.api.dto.common.TableHeaderConf;
import com.jdec.platform.common_business.api.dto.request.PersonalizedModuleReq;
import com.jdec.platform.common_business.api.dto.response.PersonalizedModuleResp;
import java.util.List;

public interface PersonalizedUserServiceApi {

    public List<PersonalizedModuleResp> getUserPersonalizedModuleSetting(
            Long userId, Long subjectId, Long roleId, String projectNo);

    public Boolean saveUserPersonalizedModuleSetting(
            Long userId, String projectNo, List<PersonalizedModuleReq> reqs);

    public Boolean restoreToDefaultSetting(
            Long userId, Long roleId, Long subjectId, String projectNo, Long moduleId);

    public PersonalizedModuleResp getUserPersonalizedModuleSettingByModuleId(
            Long userId, Long roleId, Long subjectId, String projectNo, Long moduleId);

    public List<TableHeaderConf> getUserPersonalizedModuleHeader(
            String projectNo, Long subjectId, Long roleId, Long loginUserId, Long moduleId);
}
