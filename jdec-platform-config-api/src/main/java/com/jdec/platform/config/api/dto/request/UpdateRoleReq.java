package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.dto.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 更新角色请求
 *
 * <p>用于更新现有的角色信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "更新角色请求")
public class UpdateRoleReq extends BaseRequest {

    /**
     * 角色 ID
     */
    @NotNull(message = "角色 ID 不能为空")
    @Schema(description = "角色 ID", example = "1")
    private Long id;

    /**
     * 角色名称
     */
    @Schema(description = "角色名称", example = "管理员")
    private String roleName;

    /**
     * 角色描述
     */
    @Schema(description = "角色描述", example = "系统管理员角色")
    private String description;

    @Override
    public void validate() {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("角色 ID 不能为空或无效");
        }
        if ((roleName == null || roleName.trim().isEmpty()) && 
            (description == null || description.trim().isEmpty())) {
            throw new IllegalArgumentException("至少需要提供一个更新字段");
        }
    }
}
