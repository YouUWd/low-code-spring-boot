package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色关联用户请求
 *
 * <p>包含用户ID和时效信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "角色关联用户请求")
public class RoleUserReq {

    @NotBlank(message = "用户ID不能为空")
    @Schema(description = "用户ID", example = "1")
    private String userId;

    @Schema(description = "用户类型 1-内部用户 2-外部用户", example = "1")
    private Integer userType;

    @Schema(description = "用户名称", example = "张三")
    private String userName;

    @Schema(description = "用户头像")
    private String userAvatar;

    @Schema(description = "用户工号", example = "EMP001")
    private String workNumber;

    @Schema(description = "性别 1-男 2-女", example = "1")
    private Integer sex;

    @Schema(description = "有效期开始", example = "2026-01-01")
    private LocalDate effectiveStartDate;

    @Schema(description = "有效期结束", example = "2026-12-31")
    private LocalDate effectiveEndDate;

    @Schema(description = "有效期类型 1-永久 2-自定义", example = "1")
    private Integer effectiveType;

    @Schema(description = "状态(0-有效，1-失效)", example = "0")
    private Integer status;
}
