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

    @Schema(description = "所有模块的字段权限配置列表")
    private List<ModuleFieldPermissionItem> fields;

    @Schema(description = "勾选标识：1=勾选新增，0=取消勾选")
    private Integer checkFlag;

    @Schema(description = "权限路径字符串列表，用于审计日志")
    private List<String> permissionPaths;

    @Schema(description = "权限类型名称（如：模块权限-业务菜单权限、模块权限-系统菜单权限），用于审计日志")
    private String permissionTypeName;

    @Data
    @Schema(description = "单个模块的字段权限配置明细")
    public static class ModuleFieldPermissionItem implements Serializable {

        @Schema(description = "模块ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "模块ID不能为空")
        private Long moduleId;

        @Schema(description = "可读字段ID列表")
        private List<Long> readableFields;

        @Schema(description = "可写(新增)字段ID列表")
        private List<Long> writableFields;

        @Schema(description = "可更新字段ID列表")
        private List<Long> updatableFields;
    }
}
