package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 个性化用户列 */
@TableName("personalized_user_column")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalizedUserColumn extends BaseHrEntity {
    private Integer userId;
    private Integer moduleId;
    private String columns;
}
