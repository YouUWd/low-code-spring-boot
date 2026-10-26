package com.jdec.platform.hr.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建用户基本信息请求 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserBaseReq {
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
    private String birthday;
    private Integer hometown;
    private String hometownName;
    private Integer nationality;
    private Integer politicalAppearance;
    private Integer highestEducation;
    private String graduationCollege;
    private String address;
    private Integer marital;
}
