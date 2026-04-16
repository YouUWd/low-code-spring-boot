package com.jdec.platform.auth.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户条目响应
 *
 * <p>用于列表中的单个用户条目，包含用户的基本信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntryResp {
    /** 用户ID */
    private Long id;

    /** 用户名 */
    private String userName;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 用户状态 */
    private Integer status;
}
