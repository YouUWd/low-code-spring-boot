package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 人员岗位记录表 */
@TableName("user_job")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserJob extends BaseHrEntity {
    private Integer userId;
    private Integer departmentId;
    private Integer jobId;
    private String jobName;
    private Integer subjectId;
}
