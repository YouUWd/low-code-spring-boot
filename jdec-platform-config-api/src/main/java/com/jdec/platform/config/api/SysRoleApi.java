package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.QueryRoleConfigPageReq;
import com.jdec.platform.config.api.dto.request.SaveRoleMenuReq;
import com.jdec.platform.config.api.dto.request.SaveRoleUserReq;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.shared.model.PageResult;
import java.util.List;

public interface SysRoleApi {
    List<RoleListResp> listRole();

    /**
     * 根据角色ID查询菜单列表
     *
     * @param roleId 角色ID
     * @param subjectId 主体ID
     * @param appCode 应用编码
     * @return 角色菜单列表
     */
    List<RoleMenuResp> getRoleMenu(Long roleId, Long subjectId, String appCode);

    /**
     * 保存角色菜单关联
     *
     * @param request 保存请求
     */
    void saveRoleMenu(SaveRoleMenuReq request);

    /**
     * 查询用户角色数
     *
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

    List<SubjectOptionResp> listSubjectOptions();
}
