package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.audit.annotation.AuditField;
import com.jdec.platform.shared.audit.enums.FieldType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 菜单拖拽请求 */
@Data
@Schema(description = "菜单拖拽请求")
public class SysMenuDragReq {

    @AuditField(name = "菜单ID", ignore = true)
    @Schema(description = "被拖拽节点ID")
    private Long id;

    @AuditField(
            name = "目标父菜单",
            type = FieldType.RELATION,
            target = "sys_menu",
            idField = "id",
            nameField = "title")
    @Schema(description = "目标父节点ID，拖到顶层传0")
    private Long targetPid;

    @AuditField(name = "排序值", ignore = true)
    @Schema(description = "目标排序值")
    private Integer sortOrder;
}
