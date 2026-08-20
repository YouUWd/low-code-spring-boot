package com.jdec.platform.hr.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建用户请求 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserReq {
    private String userName;
    private String nickName;
    private String userAvatar;
    private Integer sex;
    private Integer subjectId;
    private String shortName;
    private String workNumber;
    private String phone;
    private String departmentId;
    private String departmentName;
    private Integer performanceType;
    private Integer userStatus;
    private Integer jobStatus;

    public boolean isValid() {
        return userName != null
                && !userName.trim().isEmpty()
                && phone != null
                && !phone.trim().isEmpty();
    }

    public void validate() {
        if (!isValid()) {
            throw new IllegalArgumentException("用户名和手机号不能为空");
        }
    }
}
