package com.jdec.platform.auth.api;

import com.jdec.platform.auth.api.dto.request.SelectRoleReq;
import com.jdec.platform.auth.api.dto.response.CurrentUserCheckResp;
import com.jdec.platform.auth.api.dto.response.LoginResult;
import com.jdec.platform.auth.api.dto.response.SelectRoleResp;
import com.jdec.platform.shared.model.ApiResponse;

/** Auth 模块暴露的 API */
public interface AuthApi {

    LoginResult login(String token);

    CurrentUserCheckResp getCurrentUserCheckInfo(String phone);

    Boolean logout(String token);

    /**
     * 切换角色：解析出最终主体/角色，返回主体列表与菜单树，并刷新 Redis 中的当前角色缓存。
     *
     * <p>返回 {@link ApiResponse} 而非纯 DTO，是为了忠实保留源项目契约：当角色无权限时需返回 业务码 804 且仍携带 {@code
     * data}（主体列表与菜单树），纯 DTO 无法表达「非 200 业务码 + 数据」的组合。
     */
    ApiResponse<SelectRoleResp> selectRole(SelectRoleReq req);
}
