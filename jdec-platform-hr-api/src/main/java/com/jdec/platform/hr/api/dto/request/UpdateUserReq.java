package com.jdec.platform.hr.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 更新用户请求 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserReq {
    private Long id;
    private String userName;
    private String nickName;
    private String userAvatar;
    private Integer sex;
    private String shortName;
    private String workNumber;
    private String phone;
    private String departmentId;
    private String departmentName;
    private Integer performanceType;
    private Integer userStatus;
    private Integer jobStatus;

    public boolean hasChanges() {
        return userName != null
                || nickName != null
                || userAvatar != null
                || sex != null
                || shortName != null
                || workNumber != null
                || phone != null
                || departmentId != null
                || departmentName != null
                || performanceType != null
                || userStatus != null
                || jobStatus != null;
    }

    public void validate() {
        if (!hasChanges()) {
            throw new IllegalArgumentException("至少需要更新一个字段");
        }
    }
}
