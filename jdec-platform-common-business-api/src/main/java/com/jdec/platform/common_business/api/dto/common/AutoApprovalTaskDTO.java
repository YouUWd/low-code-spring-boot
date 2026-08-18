package com.jdec.platform.common_business.api.dto.common;

import java.io.Serializable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AutoApprovalTaskDTO implements Serializable {

    private Long moduleId;

    private Long recordId;

    private Long menuId;

    private Long subjectId;

    private Long roleId;

    private String autoApprovalTime;
}
