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

    /** 用户所属主体ID */
    private Long subjectId;

    /** 登录时间 */
    private LocalDateTime loginTime;

    /** 登录IP */
    private String loginIp;

    /** 登录token */
    private String token;

    /** 当前访问页面URL（从请求头 href-url 获取） */
    private String hrefUrl;

    /** 当前角色ID（从请求头 role-id 获取） */
    private Long roleId;

    /** 模拟登录的用户ID（从请求头 mock-user-id 获取） */
    private Long mockUserId;

    /** 是否为模拟登录（mockUserId != null && mockUserId != userId） */
    private Boolean isMockLogin;

    /** 真实操作用户ID（模拟登录时为 token 中的 userId，非模拟登录时与 userId 相同） */
    private Long realUserId;
}
