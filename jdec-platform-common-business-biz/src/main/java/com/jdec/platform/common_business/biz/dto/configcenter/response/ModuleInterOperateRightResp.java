package com.jdec.platform.common_business.biz.dto.configcenter.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 模块对应的互动权限返回数据模型
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModuleInterOperateRightResp {

    private List<InterOperateRightResp> applyPermissions;

    private List<InterOperateRightResp> editPermissions;

    private List<InterOperateRightResp> viewPermissions;
}
