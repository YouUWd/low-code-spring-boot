package com.jdec.platform.hr.api.bo;

import java.io.Serializable;
import lombok.Data;

@Data
public class UserBO implements Serializable {
    private Long id;
    private String userName;
    private String nickName;
    private String userAvatar;
    private Integer originalUserId;
    private String originalWorkNumber;
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
    private Integer state;
    private Integer createdBy;
    private Integer createdDate;
    private Integer updatedBy;
    private Integer updatedDate;
    private Integer isDelete;
    private String jobId;
    private String jobName;
    private String originalJob;
    private Integer age;
    private Integer jobClass;
    private String originalJobClass;
    private Integer jobLevel;
    private String birthday;
    private Integer hometown;
    private String hometownName;
    private Integer jobGrade;
    private Integer internalAbilityEvaluation;
    private Integer externalAbilityEvaluation;
    private String userIDCard;
    private Integer bankType;
    private String account;
    private String bank;
    private Double totalMoney;
    private Integer highestEducation;
    private String graduationCollege;
    private Integer dayHighestEducation;
    private String dayGraduationCollege;
    private Integer workYear;
    private String contractType;
    private String certificateList;
    private Integer enterDate;
    private Integer resignationDate;
    private Integer wageType;
    private Integer isHistory;
}
