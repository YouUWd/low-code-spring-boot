package com.jdec.platform.common_business.api.dto.request;

import java.io.Serializable;
import lombok.Data;

@Data
public class SelectRoleReq implements Serializable {
    // private Long userId;

    private Long roleId;

    private Long subjectId;

    private Long menuId;
}
