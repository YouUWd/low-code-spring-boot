package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 部门表 */
@TableName("department")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Department extends BaseHrEntity {
    private Integer subjectId;
    private Integer pid;
    private Integer type;
    private String departNumber;
    private String name;
    private Integer managerId;
    private String managerName;
    private Integer childNum;
    private Integer employeeNum;
    private Integer status;
    private Integer withOrg;
    private Integer sortOrder;
    private String description;
}
