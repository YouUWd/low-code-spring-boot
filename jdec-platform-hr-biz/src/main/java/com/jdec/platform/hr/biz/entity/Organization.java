package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 组织架构 */
@TableName("organization")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Organization extends BaseHrEntity {
    private Integer subjectId;
    private String businessNo;
    private String number;
    private String organizationContent;
    private Integer sourceId;
    private Integer versionNo;
    private Integer statusGroupId;
    private Integer statusId;
    private Integer statusValue;
    private Integer currentStep;
    private String reason;
    private Integer isHistory;
    private Integer createdBy;
    private Integer createdDate;
    private Integer updatedBy;
    private Integer updatedDate;
    private Integer isDelete;
}
