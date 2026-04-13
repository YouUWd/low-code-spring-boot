package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 社保 */
@TableName("user_social_security")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSocialSecurity extends BaseHrEntity {
    private Integer userId;
    private Integer item;
    private Integer clazz;
    private Integer secrecyLevel;
    private Integer isProvide;
    private String money;
    private String paymentMonths;
    private Integer startDate;
    private String paymentPlace;
    private String note;
    private String groupHash;
}
