package com.jdec.platform.shared.security.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 登录用户信息 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 手机号 */
    private String phone;

    /** 头像 */
    private String avatar;

    /** 是否超级管理员 */
    private Boolean superAdmin;

    /** 当前项目编号 */
    private String projectNo;

    /** 登录时间 */
    private LocalDateTime loginTime;

    /** 登录IP */
    private String loginIp;

    /** 登录token */
    private String token;
}
