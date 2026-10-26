package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 印章 */
@TableName("user_seal")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSeal extends BaseHrEntity {
    private Integer userId;
    private Integer classId;
    private String sealNumber;
    private Integer sealUnit;
    private Integer consumingDate;
    private Integer consumingNum;
    private String note;
    private String groupHash;
}
