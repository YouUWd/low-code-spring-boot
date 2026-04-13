package com.jdec.platform.auth.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 员工基本信息响应
 *
 * <p>用于登录响应中返回员工信息，避免 auth.api 依赖 hr.api
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeInfoResp {
    /** 员工ID */
    private Long id;

    /** 员工名称 */
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

    /** 员工状态 */
    private Integer userStatus;
}
