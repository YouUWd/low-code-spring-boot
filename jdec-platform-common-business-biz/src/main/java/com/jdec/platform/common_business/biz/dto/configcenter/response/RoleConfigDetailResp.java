package com.jdec.platform.common_business.biz.dto.configcenter.response;

import java.util.List;
import lombok.Data;

@Data
public class RoleConfigDetailResp {

    private Long id;

    private String roleName;

    private List<Long> subjectIds;

    private List<String> userInsideIds;

    private List<String> userExternalIds;

    // ... 还很多，需要 自行补充
}
