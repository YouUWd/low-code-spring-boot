package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 工作经历 */
@TableName("user_work")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWork extends BaseHrEntity {
    private Integer userId;
    private Integer entryDate;
    private Integer resignDate;
    private String company;
    private String department;
    private String job;
    private String note;
}
