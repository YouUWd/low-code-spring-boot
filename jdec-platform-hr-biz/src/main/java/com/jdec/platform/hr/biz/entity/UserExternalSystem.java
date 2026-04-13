package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 外部系统 */
@TableName("user_external_system")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserExternalSystem extends BaseHrEntity {
    private Integer userId;
    private Integer type;
    private String systemName;
    private String url;
    private String account;
    private String loginType;
    private Integer registrationDate;
    private Integer stopDate;
    private Integer authorizedStartDate;
    private Integer authorizedEndDate;
    private String role;
    private String cardId;
    private String phone;
    private String customerCode;
    private String operationCode;
    private String loginPass;
    private String note;
    private String groupHash;
}
