package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.dto.BaseRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 创建权限请求
 *
 * <p>用于创建新的权限。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "创建权限请求")
public class CreatePermissionReq extends BaseRequest {

    /**
     * 模块 ID
     */
    @NotNull(message = "模块 ID 不能为空")
    @Schema(description = "模块 ID", example = "1")
    private Long moduleId;

    /**
     * 字段编码，* 表示模块级
     */
    @NotBlank(message = "字段编码不能为空")
    @Schema(description = "字段编码，* 表示模块级", example = "*")
    private String fieldCode;

    /**
     * 权限类型 (READ/WRITE/UPDATE/DELETE)
     */
    @NotBlank(message = "权限类型不能为空")
    @Schema(description = "权限类型", example = "READ")
    private String permissionType;

    /**
     * 权限编码 (全局唯一)
     */
    @NotBlank(message = "权限编码不能为空")
    @Schema(description = "权限编码", example = "user_base:*:read")
    private String permissionCode;

    /**
     * 权限名称
     */
    @NotBlank(message = "权限名称不能为空")
    @Schema(description = "权限名称", example = "查看用户基本信息")
    private String permissionName;

    /**
     * 权限描述
     */
    @Schema(description = "权限描述", example = "允许查看用户基本信息")
    private String description;

    @Override
    public void validate() {
        if (moduleId == null || moduleId <= 0) {
            throw new IllegalArgumentException("模块 ID 不能为空或无效");
        }
        if (fieldCode == null || fieldCode.trim().isEmpty()) {
            throw new IllegalArgumentException("字段编码不能为空");
        }
        if (permissionType == null || permissionType.trim().isEmpty()) {
            throw new IllegalArgumentException("权限类型不能为空");
        }
        if (permissionCode == null || permissionCode.trim().isEmpty()) {
            throw new IllegalArgumentException("权限编码不能为空");
        }
        if (permissionName == null || permissionName.trim().isEmpty()) {
            throw new IllegalArgumentException("权限名称不能为空");
        }
    }
}
