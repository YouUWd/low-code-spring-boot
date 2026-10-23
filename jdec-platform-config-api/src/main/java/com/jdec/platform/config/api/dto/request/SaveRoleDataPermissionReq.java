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

    @Schema(description = "权限类型名称（如：模块权限-业务菜单权限、模块权限-系统菜单权限），用于审计日志")
    private String permissionTypeName;

    @Data
    @Schema(description = "数据权限节点保存请求")
    public static class SaveDataPermissionReq {
        @Schema(description = "权限节点id")
        private Long permissionId;

        @Schema(description = "数据权限全选：1=全选，0=非全选")
        private Integer permissionCheckAll;

        @Schema(description = "主体列表")
        private List<SaveDataSubjectReq> subjects;
    }

    @Data
    @Schema(description = "数据权限主体保存请求")
    public static class SaveDataSubjectReq {
        @Schema(description = "主体ID")
        private Long subjectId;

        @Schema(description = "主体全选：1=全选，0=非全选")
        private Integer subjectCheckAll;

        @Schema(description = "业务值列表，支持字符串、数字、布尔、JSON 对象/数组，入库时序列化为 JSON 文本")
        private List<Object> bizNames;
    }
}
