package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 技能/语言 */
@TableName("user_skill")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSkill extends BaseHrEntity {
    private Integer userId;
    private String skillName;
    private String proficiency;
    private String skillDuration;
    private String note;
}
