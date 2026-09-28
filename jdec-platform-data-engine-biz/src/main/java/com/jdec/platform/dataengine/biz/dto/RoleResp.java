package com.jdec.platform.dataengine.biz.dto;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleResp implements Serializable {

    private Long roleId;

    private String roleName;

    private String roleSlug;

    private Long subjectId;

    private Integer effectiveType;

    private String effectiveStartDate;

    private String effectiveEndDate;

    private Integer roleSource;
}
