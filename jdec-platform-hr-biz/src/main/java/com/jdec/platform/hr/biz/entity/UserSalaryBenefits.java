package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 薪酬福利 */
@TableName("user_salary_benefits")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSalaryBenefits extends BaseHrEntity {
    private Integer userId;
    private Integer wageType;
    private Integer performanceType;
}
