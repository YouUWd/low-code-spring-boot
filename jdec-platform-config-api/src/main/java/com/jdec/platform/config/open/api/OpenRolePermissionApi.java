package com.jdec.platform.config.open.api;

import com.jdec.platform.config.api.dto.response.DataRightConfigResp;
import java.util.List;

public interface OpenRolePermissionApi {
    /**
     * 根据角色ID查询数据权限详情（包含业务数据列表）
     *
     * @param roleId 角色ID
     * @return 角色数据权限详情列表
     */
    List<DataRightConfigResp> getRoleDataPermissionDetail(Long roleId);
}
