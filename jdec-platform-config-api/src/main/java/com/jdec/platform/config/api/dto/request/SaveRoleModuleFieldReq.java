package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 保存角色模块字段权限请求 */
@Data
@Schema(description = "保存角色模块字段权限请求")
public class SaveRoleModuleFieldReq {

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "字段权限列表")
    private List<FieldPermissionItem> permissions;

    @Data
    @Schema(description = "字段权限项")
    public static class FieldPermissionItem {

        @Schema(description = "字段类型: simple-简单字段, combine-组合字段")
        private String fieldType;

        @Schema(description = "字段ID")
        private Long fieldId;

        @Schema(description = "是否可查看")
        private Integer readable;

        @Schema(description = "是否可申请")
        private Integer writable;

        @Schema(description = "是否可编辑")
        private Integer updatable;
    }
}
