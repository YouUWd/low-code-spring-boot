package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.dto.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 更新权限请求
 *
 * <p>用于更新现有的权限信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "更新权限请求")
public class UpdatePermissionReq extends BaseRequest {

    /**
     * 权限 ID
     */
    @NotNull(message = "权限 ID 不能为空")
    @Schema(description = "权限 ID", example = "1")
    private Long id;

    /**
     * 权限类型 (READ/WRITE/UPDATE/DELETE)
     */
    @Schema(description = "权限类型", example = "READ")
    private String permissionType;

    /**
     * 权限编码 (全局唯一)
     */
    @Schema(description = "权限编码", example = "user_base:*:read")
    private String permissionCode;

    /**
     * 权限名称
     */
    @Schema(description = "权限名称", example = "查看用户基本信息")
    private String permissionName;

    /**
     * 权限描述
     */
    @Schema(description = "权限描述", example = "允许查看用户基本信息")
    private String description;

    @Override
    public void validate() {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("权限 ID 不能为空或无效");
        }
        if ((permissionType == null || permissionType.trim().isEmpty()) && 
            (permissionCode == null || permissionCode.trim().isEmpty()) &&
            (permissionName == null || permissionName.trim().isEmpty()) &&
            (description == null || description.trim().isEmpty())) {
            throw new IllegalArgumentException("至少需要提供一个更新字段");
        }
    }
}
