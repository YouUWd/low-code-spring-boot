package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 职业资格证书信息 */
@TableName("user_certificate_qualification")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCertificateQualification extends BaseHrEntity {
    private Integer userId;
    private Integer qualifocateType;
    private Integer qualifocateClass;
    private Integer qualifocateName;
    private Integer qualifocateLevel;
    private Integer operationItems;
    private String major;
    private String certificateNumber;
    private String registerNumber;
    private Integer isAnnual;
    private Integer startDate;
    private Integer endDate;
    private String authority;
    private String certificateFile;
    private String groupHash;
}
