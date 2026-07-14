package com.jdec.platform.common_business.biz.dto.configcenter.req;

import java.io.Serializable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CheckUserSpecialPermissionReq implements Serializable {

    private Long userId;

    private String rightCode;
}
