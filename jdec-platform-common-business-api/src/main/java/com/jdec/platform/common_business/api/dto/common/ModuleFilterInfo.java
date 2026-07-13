package com.jdec.platform.common_business.api.dto.common;

import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModuleFilterInfo {

    private ModuleInfo moduleInfo;

    // 有权限的字段
    private List<ModuleField> simpleModuleFields;

    // 有权限的组合字段
    private List<ModuleField> combineModuleFields;

    // 模块所有展示的字段列表
    private List<ModuleField> allFields;

    private List<ModuleTable> moduleTableList;

    private Set<FormButton> btnList;

    private Set<InterOperateRight> interOperateRights;
}
