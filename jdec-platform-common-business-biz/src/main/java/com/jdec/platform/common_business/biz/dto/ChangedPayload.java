package com.jdec.platform.common_business.biz.dto;

import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class ChangedPayload implements Serializable {

    private List<Long> affectedSubjectIds;

    private List<Long> affectedUserIds;

    private List<Long> affectedRoleIds;

    private List<Long> affectedModuleIds;

    private Boolean simulateLogin;
}
