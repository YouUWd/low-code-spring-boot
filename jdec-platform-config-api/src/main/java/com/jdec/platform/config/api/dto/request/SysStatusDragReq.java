package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.audit.annotation.AuditField;
import com.jdec.platform.shared.audit.enums.FieldType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 状态拖拽请求 */
@Data
@Schema(description = "状态拖拽请求")
public class SysStatusDragReq {

    @AuditField(name = "状态ID", ignore = true)
    @Schema(description = "被拖拽节点ID")
    private Long id;

    @AuditField(
            name = "目标父状态",
            type = FieldType.RELATION,
            target = "sys_status",
            idField = "id",
            nameField = "title")
    @Schema(description = "目标父节点ID，拖到顶层传0")
    private Long targetPid;

    @AuditField(name = "排序值")
    @Schema(description = "排序值，从0开始")
    private Integer sortOrder;
}
