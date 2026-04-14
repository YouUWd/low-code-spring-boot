package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.annotation.SysModule;
import com.jdec.platform.shared.dto.BaseRequest;
import com.jdec.platform.shared.enums.PermissionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 创建角色请求
 *
 * <p>用于创建新的角色。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "创建角色请求")
@SysModule(moduleCode = "CreateRole", moduleName = "新增角色",description = "新增角色", permissionTypes = PermissionType.WRITE)
public class CreateRoleReq extends BaseRequest {

    /**
     * 角色编码
     */
    @NotBlank(message = "角色编码不能为空")
    @Schema(description = "角色编码", example = "admin")
    private String roleCode;

    /**
     * 角色名称
     */
    @NotBlank(message = "角色名称不能为空")
    @Schema(description = "角色名称", example = "管理员")
    private String roleName;

    /**
     * 角色描述
     */
    @Schema(description = "角色描述", example = "系统管理员角色")
    private String description;

    @Override
    public void validate() {
        if (roleCode == null || roleCode.trim().isEmpty()) {
            throw new IllegalArgumentException("角色编码不能为空");
        }
        if (roleName == null || roleName.trim().isEmpty()) {
            throw new IllegalArgumentException("角色名称不能为空");
        }
    }
}
