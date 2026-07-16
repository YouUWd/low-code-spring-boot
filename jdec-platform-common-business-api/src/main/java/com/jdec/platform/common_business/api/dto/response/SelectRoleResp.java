package com.jdec.platform.common_business.api.dto.response;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

@Data
public class SelectRoleResp implements Serializable {

    List<SubjectResp> subjectList;

    List<MenuResp> menuList;

    private Long subjectId;

    private Long roleId;

    private Long menuId;

    // false 没有   true  有 模拟权限
    private Boolean simulateLogin;
}
