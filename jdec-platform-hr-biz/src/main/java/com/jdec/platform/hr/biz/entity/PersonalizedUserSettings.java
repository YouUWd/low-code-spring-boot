package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 个性化用户设置 */
@TableName("personalized_user_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalizedUserSettings extends BaseHrEntity {
    private Integer userId;
    private String settingType;
    private Integer personalizedSettingId;
    private String personalizedSettingSlug;
    private String extraProperty;
    private Integer isDelete;
}
