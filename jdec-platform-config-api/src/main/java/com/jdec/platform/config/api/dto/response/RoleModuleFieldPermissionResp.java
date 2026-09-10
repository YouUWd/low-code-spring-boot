package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 角色模块字段权限响应（按模块聚合，与保存入参完全对称） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "角色模块字段权限响应")
public class RoleModuleFieldPermissionResp implements Serializable {

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "可查看/浏览字段ID列表")
    private List<Long> viewFields;

    @Schema(description = "可申请/填报(新增)字段ID列表")
    private List<Long> applyFields;

    @Schema(description = "可编辑/修改字段ID列表")
    private List<Long> editFields;
}
