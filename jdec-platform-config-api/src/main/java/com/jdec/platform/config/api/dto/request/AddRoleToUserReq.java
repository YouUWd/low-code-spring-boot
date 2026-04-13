package com.jdec.platform.config.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 为用户添加角色请求 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddRoleToUserReq {
    /** 用户 ID */
    private Long userId;

    /** 角色 ID */
    private Long roleId;
}
