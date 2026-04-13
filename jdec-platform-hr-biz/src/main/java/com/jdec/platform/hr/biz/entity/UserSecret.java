package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** U盾/密匙 */
@TableName("user_secret")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSecret extends BaseHrEntity {
    private Integer userId;
    private String secretName;
    private String secretUrl;
    private String account;
    private Integer secretType;
    private String secretNumber;
    private Integer secretNum;
    private Integer secretUnit;
    private String certificateValidity;
    private Integer consumingDate;
    private String note;
    private String groupHash;
}
