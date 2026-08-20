package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 教育经历 */
@TableName("user_educate")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEducate extends BaseHrEntity {
    private Integer userId;
    private Integer startDate;
    private Integer endDate;
    private String college;
    private Integer education;
    private Integer degree;
    private Integer studyType;
    private Integer educationType;
    private Integer educationHeight;
    private Integer educationFullTimeHeight;
    private String major;
    private Integer majorType;
    private Integer academicType;
    private String academicLevel;
    private Integer diplomaSendDate;
    private String diplomaFile;
    private Integer degreeSendDate;
    private String degreeFile;
    private Integer isHigh;
    private Integer isStudyHigh;
    private String groupHash;
}
