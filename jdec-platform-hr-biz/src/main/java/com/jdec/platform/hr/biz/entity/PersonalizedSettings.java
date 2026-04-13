package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 个性化设置 */
@TableName("personalized_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalizedSettings extends BaseHrEntity {
    private String settingType;
    private Integer moduleId;
    private String settingName;
    private String settingSlug;
    private Integer createdBy;
    private Integer createdDate;
    private Integer updatedBy;
    private Integer updatedDate;
    private Integer isDelete;
}
