package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

/** 保存角色模块字段权限请求 */
@Data
@Schema(description = "保存角色模块字段权限请求")
public class SaveRoleModuleFieldPermissionReq implements Serializable {

    @Schema(description = "角色ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "角色ID不能为空")
    private Long roleId;

    @Schema(description = "模块ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "模块ID不能为空")
    private Long moduleId;

    @Schema(description = "字段权限配置明细列表")
    private List<FieldPermissionItem> fields;

    @Data
    @Schema(description = "字段权限明细")
    public static class FieldPermissionItem implements Serializable {

        @Schema(description = "字段ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "字段ID不能为空")
        private Long fieldId;

        @Schema(description = "是否可查看 (0-否, 1-是)")
        private Integer readable;

        @Schema(description = "是否可申请 (0-否, 1-是)")
        private Integer writable;

        @Schema(description = "是否可编辑 (0-否, 1-是)")
        private Integer updatable;
    }
}
