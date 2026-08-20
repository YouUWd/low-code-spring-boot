package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 家庭关系 */
@TableName("user_family")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFamily extends BaseHrEntity {
    private Integer userId;
    private String call;
    private String name;
    private String tel;
    private String company;
    private Integer isContact;
    private String note;
}
