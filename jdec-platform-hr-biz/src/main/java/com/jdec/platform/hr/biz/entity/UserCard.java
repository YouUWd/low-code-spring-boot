package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 卡号信息 */
@TableName("user_card")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCard extends BaseHrEntity {
    private Integer userId;
    private Integer cardType;
    private Integer bankType;
    private Integer isSend;
    private String account;
    private String bank;
    private String note;
    private String groupHash;
}
