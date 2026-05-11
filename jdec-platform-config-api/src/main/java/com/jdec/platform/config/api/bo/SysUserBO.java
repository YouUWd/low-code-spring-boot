package com.jdec.platform.config.api.bo;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SysUserBO implements Serializable {
    private Long id;

    private Long userId;

    private Long companyId;

    private Long subjectId;

    private String departIds;

    private Integer userType;

    private String userName;

    private String workNumber;
    private String userAvatar;

    private String phone;

    private String shortName;

    private Integer sex;

    private String companyName;

    private String departName;

    private String leaderName;

    private String positionName;

    private String subjectName;

    private String password;

    private Integer statusFlag;

    private Integer employedStatus;

    private Integer superFlag;

    private String projectNo;

    private Long createdBy;

    private String createdName;

    private LocalDateTime createdDate;

    private Long updatedBy;

    private String updatedName;

    private LocalDateTime updatedDate;

    private Integer deleted;
}
