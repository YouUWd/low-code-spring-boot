package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 残疾证信息 */
@TableName("user_certificate_disability")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCertificateDisability extends BaseHrEntity {
    private Integer userId;
    private Integer disabilityType;
    private Integer disabilityLevel;
    private String disabilityNumber;
    private Integer startDate;
    private Integer certificateValidity;
    private String authority;
    private Integer isValidity;
    private String certificateFile;
    private String note;
    private String groupHash;
}
