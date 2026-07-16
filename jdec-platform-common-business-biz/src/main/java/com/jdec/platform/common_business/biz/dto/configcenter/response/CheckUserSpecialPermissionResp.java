package com.jdec.platform.common_business.biz.dto.configcenter.response;

import java.io.Serializable;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class CheckUserSpecialPermissionResp implements Serializable {

    private Boolean hasPermission;
}
