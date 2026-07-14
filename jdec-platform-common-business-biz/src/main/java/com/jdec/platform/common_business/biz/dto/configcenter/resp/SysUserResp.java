package com.jdec.platform.common_business.biz.dto.configcenter.resp;

import java.io.Serializable;
import lombok.Data;

@Data
public class SysUserResp implements Serializable {

    private Long id;

    private Long userId;

    private Long companyId;

    private Long subjectId;

    private Integer userType;

    private String userName;

    private String workNumber;

    private String userAvatar;

    private String phone;

    private Integer sex;

    private String companyName;

    private String departName;

    private String leaderName;

    private String positionName;

    private String subjectName;

    private Integer statusFlag;

    private Integer employedStatus;

    private Integer superFlag;

    private String createdDate;
}
