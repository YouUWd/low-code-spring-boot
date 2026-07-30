package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 角色交互权限保存请求 */
@Data
@Schema(description = "角色交互权限保存请求")
public class SaveRoleInteractionPermissionReq {

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "权限配置列表")
    private List<PermissionItem> permissions;

    @Schema(description = "勾选标识：1=勾选新增，0=取消勾选")
    private Integer checkFlag;

    @Schema(description = "权限路径字符串列表，用于审计日志")
    private List<String> permissionPaths;

    @Schema(description = "权限类型名称（如：模块权限-业务菜单权限、模块权限-系统菜单权限），用于审计日志")
    private String permissionTypeName;

    /** 权限项 */
    @Data
    @Schema(description = "权限项")
    public static class PermissionItem {

        @Schema(description = "模块ID")
        private Long moduleId;

        @Schema(description = "交互权限ID列表")
        private List<Long> permissionIds;
    }
}
