package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.QueryRoleConfigPageReq;
import com.jdec.platform.config.api.dto.request.SaveRoleUserReq;
import com.jdec.platform.config.api.dto.response.CheckRoleUserResp;
import com.jdec.platform.config.api.dto.response.RoleConfigDetailResp;
import com.jdec.platform.config.api.dto.response.RoleConfigPageResp;
import com.jdec.platform.config.api.dto.response.RoleUserTimeResp;
import com.jdec.platform.shared.model.PageResult;

public interface SysRoleApi {
    /**
     * 查询用户角色数
     * @param id
     * @param subjectId
     * @param appCode
     * @return
     */
    Long getUserRoleCount(Long id, Long subjectId, String appCode);

    CheckRoleUserResp checkRoleUser(SaveRoleUserReq request);

    void saveRoleUser(SaveRoleUserReq request);

    RoleUserTimeResp getRoleUserTime(String userId, Long roleId);

    PageResult<RoleConfigPageResp> getRoleConfigPage(QueryRoleConfigPageReq query);

    RoleConfigDetailResp getRoleConfigDetail(Long roleId, Integer userType);

    void deleteRole(Long roleId);
}
