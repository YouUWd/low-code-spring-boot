package com.jdec.platform.hr.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户基本信息响应 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBasicInfoResp {
    /** 用户ID */
    private Long id;

    /** 用户名 */
    private String userName;

    /** 手机号 */
    private String phone;

    /** 主体ID */
    private Integer subjectId;

    /** 企业微信用户ID */
    private String shortName;

    /** 工号 */
    private String workNumber;

    /** 部门名称 */
    private String departmentName;

    /** 部门id */
    private String deptIds;

    /** 用户状态 */
    private Integer userStatus;
}
