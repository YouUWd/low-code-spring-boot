package com.jdec.platform.dataengine.biz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模块树结构组织模块权限按钮集合和字段权限")
public class ModuleBtnAndFieldNode {

    private Long moduleId;

    private String moduleName;

    private Set<FormButton> buttons;

    private List<ModuleField> fields;

    private List<ModuleBtnAndFieldNode> children;
}
