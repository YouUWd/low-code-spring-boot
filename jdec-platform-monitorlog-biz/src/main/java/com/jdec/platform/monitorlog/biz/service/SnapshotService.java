package com.jdec.platform.monitorlog.biz.service;

import com.jdec.platform.config.api.dto.response.RoleConfigDetailResp;
import com.jdec.platform.config.api.dto.response.UserDetailResp;

public interface SnapshotService {

    UserDetailResp getUserSnapshot(Long userId);

    RoleConfigDetailResp getRoleSnapshot(Long roleId);

    void refreshUserSnapshot(Long userId);

    void refreshRoleSnapshot(Long roleId);
}
