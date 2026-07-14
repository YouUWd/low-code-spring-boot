package com.jdec.platform.common_business.api;

import com.jdec.platform.common_business.api.dto.common.SubjectRoleDTO;
import com.jdec.platform.common_business.api.dto.request.SelectRoleReq;
import com.jdec.platform.common_business.api.dto.response.SelectRoleResp;
import com.jdec.platform.shared.model.ApiResponse;

public interface SystemManageServiceApi {

    public ApiResponse<SelectRoleResp> selectRole(SelectRoleReq req);

    public SubjectRoleDTO getUserCurrentRole(Long subjectId, Long userId);

    // 从 Redis 中获取检验 用户的 token 是否有效
    public Boolean checkUserToken(String token, String phone);

    // 从 Redis 中移除 用户的 token
    public Boolean removeUserToken(String token, String phone);
}
