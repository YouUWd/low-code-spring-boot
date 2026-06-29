package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.QueryRoleConfigPageReq;
import com.jdec.platform.config.api.dto.request.SaveRoleUserReq;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.shared.model.PageResult;
import java.util.List;

public interface SysRoleApi {
    List<RoleListResp> listRole();

    /**
     * 查询用户角色数
     *
     * @param id
     * @param subjectId
     * @param projectNo
     * @return
     */
    Long getUserRoleCount(Long id, Long subjectId, String projectNo);

    CheckRoleUserResp checkRoleUser(SaveRoleUserReq request);

    RoleConfigPageResp saveRoleUser(SaveRoleUserReq request);

    RoleUserTimeResp getRoleUserTime(String userId, Long roleId);

    PageResult<RoleConfigPageResp> getRoleConfigPage(QueryRoleConfigPageReq query);

    RoleConfigDetailResp getRoleConfigDetail(Long roleId);

    void deleteRole(Long roleId);

    List<SubjectOptionResp> listSubjectOptions();

    /**
     * 根据用户ID和主体ID查询用户有哪些角色
     *
     * @param userId 用户ID
     * @param subjectId 主体ID
     * @return 角色列表（仅返回在有效期内的角色）
     */
    List<UserRoleListResp> getUserRolesByUserIdAndSubjectId(Long userId, Long subjectId);

    /**
     * 查询用户切换所需的数据
     *
     * @param userName 用户名称（模糊查询）
     * @return 用户切换数据
     */
    UserSwitchDataResp getUserSwitchData(String userName);
}
