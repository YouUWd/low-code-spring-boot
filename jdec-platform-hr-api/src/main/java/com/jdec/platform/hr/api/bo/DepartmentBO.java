package com.jdec.platform.hr.api.bo;

import java.io.Serializable;
import lombok.Data;

@Data
public class DepartmentBO implements Serializable {
    private Long id;
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
    private String description;
    private Short sortOrder;
}
