package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.dto.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 为角色添加权限请求
 *
 * <p>用于为角色添加权限。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "为角色添加权限请求")
public class AddPermissionToRoleReq extends BaseRequest {

    /**
     * 角色 ID
     */
    @NotNull(message = "角色 ID 不能为空")
    @Schema(description = "角色 ID", example = "1")
    private Long roleId;

    /**
     * 权限 ID
     */
    @NotNull(message = "权限 ID 不能为空")
    @Schema(description = "权限 ID", example = "1")
    private Long permissionId;

    @Override
    public void validate() {
        if (roleId == null || roleId <= 0) {
            throw new IllegalArgumentException("角色 ID 不能为空或无效");
        }
        if (permissionId == null || permissionId <= 0) {
            throw new IllegalArgumentException("权限 ID 不能为空或无效");
        }
    }
}
