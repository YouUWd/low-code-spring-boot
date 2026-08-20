package com.jdec.platform.hr.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 更新用户基本信息请求 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserBaseReq {
    private Long id;
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

    public boolean hasChanges() {
        return userName != null
                || sex != null
                || jobClass != null
                || originalJobClass != null
                || positionId != null
                || shortName != null
                || jobLevel != null
                || jobGrade != null
                || enterDate != null
                || birthday != null
                || hometown != null
                || hometownName != null
                || nationality != null
                || politicalAppearance != null
                || highestEducation != null
                || graduationCollege != null
                || address != null
                || marital != null;
    }

    public void validate() {
        if (!hasChanges()) {
            throw new IllegalArgumentException("至少需要更新一个字段");
        }
    }
}
