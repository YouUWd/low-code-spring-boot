package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 联系方式 */
@TableName("user_contact")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContact extends BaseHrEntity {
    private Integer userId;
    private String userName;
    private Integer type;
    private String account;
    private String shortNumber;
    private String groupHash;
}
