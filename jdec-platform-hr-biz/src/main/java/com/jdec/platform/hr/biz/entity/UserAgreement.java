package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 合同协议 */
@TableName("user_agreement")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAgreement extends BaseHrEntity {
    private Integer userId;
    private Integer contractType;
    private Integer startDate;
    private Integer endDate;
    private String content;
    private String file;
    private String money;
    private String note;
    private String groupHash;
}
