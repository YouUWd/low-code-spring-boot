package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 角色数据权限保存请求 */
@Data
@Schema(description = "角色数据权限保存请求")
public class SaveRoleDataPermissionReq {

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "权限列表")
    private List<SaveDataPermissionReq> dataPermissions;

    @Schema(description = "勾选标识：1=勾选新增，0=取消勾选")
    private Integer checkFlag;

    @Schema(description = "权限路径字符串列表，用于审计日志")
    private List<String> permissionPaths;

    @Data
    @Schema(description = "角色数据权限保存请求")
    public static class SaveDataPermissionReq {
        @Schema(description = "权限节点id")
        private Long permissionId;

        @Schema(description = "业务名称列表")
        private List<String> bizNames;
    }
}
