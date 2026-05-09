package com.jdec.platform.shared.security;

import com.jdec.platform.shared.security.model.UserInfo;

/** Sa-Token 用户数据加载接口 */
public interface SaTokenUserDataLoader {

    /** 根据用户ID加载用户数据 */
    UserInfo getUserByPhone(String phone);

    /**
     * 通过手机号查询用户角色数
     *
     * @param phone
     * @return
     */
    Long getUserRoleCount(String phone, String appCode);
}
