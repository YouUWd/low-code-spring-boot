package com.jdec.platform.common_business.biz.dto;

import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class RoleModulePermissionChangedPayload implements Serializable {

    private List<Long> affectedSubjectIds;

    private List<Long> affectedUserIds;

    private Long roleId;

    private Long moduleId;

    private List<Long> affectedModuleIds;
}
