package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 工资 */
@TableName("user_salary")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSalary extends BaseHrEntity {
    private Integer userId;
    private Integer item;
    private Integer clazz;
    private Integer secrecyLevel;
    private Integer isProvide;
    private String money;
    private Integer startDate;
    private Integer endDate;
    private String note;
    private String groupHash;
}
