package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 信用等级 */
@TableName("user_credit_rating")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreditRating extends BaseHrEntity {
    private Integer userId;
}
