package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.dto.BaseRequest;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 为用户添加角色请求
 *
 * <p>用于为用户分配角色
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AddRoleToUserReq extends BaseRequest {

    /**
     * 用户 ID
     */
    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    /**
     * 角色 ID
     */
    @NotNull(message = "角色 ID 不能为空")
    private Long roleId;

    /**
     * 验证请求对象
     *
     * @throws IllegalArgumentException 验证失败时抛出
     */
    @Override
    public void validate() {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户 ID 不能为空或无效");
        }
        if (roleId == null || roleId <= 0) {
            throw new IllegalArgumentException("角色 ID 不能为空或无效");
        }
    }
}
