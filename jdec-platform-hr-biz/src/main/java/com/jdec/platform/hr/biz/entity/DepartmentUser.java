package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/** 部门人员记录表 - 无主键和时间戳 */
@TableName("department_user")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentUser {
    private Integer departmentId;
    private Integer userId;
    private Integer subjectId;
}
