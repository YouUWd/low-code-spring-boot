package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 角色数据权限响应 */
@Data
@Schema(description = "角色数据权限响应")
public class RoleDataPermissionResp {

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "权限列表")
    private List<DataPermissionItem> dataPermission;

    @Data
    @Schema(description = "数据权限节点响应")
    public static class DataPermissionItem {
        @Schema(description = "权限节点id")
        private Long permissionId;

        @Schema(description = "数据权限全选：1=全选，0=非全选")
        private Integer permissionCheckAll;

        @Schema(description = "主体列表")
        private List<DataPermissionSubject> subjects;
    }

    @Data
    @Schema(description = "数据权限主体响应")
    public static class DataPermissionSubject {
        @Schema(description = "主体ID")
        private Long subjectId;

        @Schema(description = "主体全选：1=全选，0=非全选")
        private Integer subjectCheckAll;

        @Schema(description = "业务名称列表")
        private List<String> bizNames;
    }
}
