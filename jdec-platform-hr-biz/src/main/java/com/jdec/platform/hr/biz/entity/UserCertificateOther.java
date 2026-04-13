package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 其他资格证书信息 */
@TableName("user_certificate_other")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCertificateOther extends BaseHrEntity {
    private Integer userId;
    private String certificateName;
    private String certificateType;
    private String certificateLevel;
    private String certificateNumber;
    private Integer startDate;
    private Integer endDate;
    private String authority;
    private String certificateValidity;
    private Integer interrogationDate;
    private String certificateFile;
    private String note;
}
