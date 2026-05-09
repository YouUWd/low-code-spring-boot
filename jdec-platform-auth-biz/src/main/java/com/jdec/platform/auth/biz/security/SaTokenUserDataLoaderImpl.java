package com.jdec.platform.auth.biz.security;

import com.jdec.platform.config.api.SysRoleApi;
import com.jdec.platform.config.api.SysUserApi;
import com.jdec.platform.config.api.bo.SysUserBO;
import com.jdec.platform.shared.security.SaTokenUserDataLoader;
import com.jdec.platform.shared.security.model.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Sa-Token 用户数据加载器实现 */
@Component
@RequiredArgsConstructor
public class SaTokenUserDataLoaderImpl implements SaTokenUserDataLoader {

    private final SysUserApi sysUserApi;
    private final SysRoleApi sysRoleApi;

    @Override
    public UserInfo getUserByPhone(String phone) {
        SysUserBO user = sysUserApi.getUserByPhone(phone);
        if (user == null) {
            return null;
        }
        return UserInfo.builder()
                .id(user.getId())
                .userId(user.getUserId())
                .username(user.getUserName())
                .password(user.getPassword())
                .status(user.getStatusFlag())
                .appCode(user.getAppCode())
                .build();
    }

    @Override
    public Long getUserRoleCount(String phone, String appCode) {
        SysUserBO user = sysUserApi.getUserByPhone(phone);
        return sysRoleApi.getUserRoleCount(user.getId(), user.getSubjectId(), appCode);
    }
}
