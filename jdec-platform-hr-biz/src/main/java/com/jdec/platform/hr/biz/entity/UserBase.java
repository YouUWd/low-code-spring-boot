package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** 用户基本信息表 */
@TableName("user_base")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserBase extends BaseHrEntity {
    private Integer userId;
    private String userName;
    private Integer sex;
    private Integer jobClass;
    private String originalJobClass;
    private Integer positionId;
    private String shortName;
    private Integer jobLevel;
    private Integer jobGrade;
    private Integer enterDate;
    private Integer resignationDate;

    @TableField("user_IDCard")
    private String userIDCard;

    @TableField("user_IDCard_start_date")
    private Long userIDCardStartDate;

    @TableField("user_IDCard_end_date")
    private Long userIDCardEndDate;

    @TableField("user_IDCard_long_effective")
    private Integer userIDCardLongEffective;

    @TableField("user_IDCard_valid_date")
    private String userIDCardValidDate;

    private String birthday;
    private Integer hometown;
    private String hometownName;
    private Integer nationality;
    private Integer politicalAppearance;
    private Integer dayHighestEducation;
    private String dayGraduationCollege;
    private Integer highestEducation;
    private String graduationCollege;
    private String address;
    private Integer marital;
    private Integer householdType;
    private String householdAddress;
    private Integer workBeforeYear;
    private Integer workCurrentYear;
    private Integer workYear;
    private Integer workEffectiveDate;
    private String carNumber;
    private String introductionPerson;
    private Integer internalAbilityEvaluation;
    private Integer externalAbilityEvaluation;
    private String userCardFront;
    private String userCardBack;
    private String drivingPermit;
    private String drivingLicense;
    private String longitude;
    private String latitude;
}
