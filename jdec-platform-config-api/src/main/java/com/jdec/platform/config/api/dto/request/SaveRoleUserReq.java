package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 保存角色及用户关联请求
 *
 * <p>用于保存角色信息及其用户关联，id为空则新增角色，不为空则修改
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "保存角色及用户关联请求")
public class SaveRoleUserReq {

    @Schema(description = "角色ID，为空则新增", example = "19")
    private Long id;

    @NotBlank(message = "角色名称不能为空")
    @Schema(description = "角色名称", example = "这就不好说了")
    private String roleName;

    @Schema(description = "内部用户ID列表", example = "[1,2]")
    private List<String> userInsideIds;

    @Schema(description = "外部用户ID列表", example = "[1]")
    private List<String> userExternalIds;

    @Schema(description = "主体ID，有值则走角色主体关联逻辑", example = "100")
    private Long subjectId;

    @Schema(description = "主体名称", example = "测试主体")
    private String subjectName;

    @Schema(description = "有效期开始", example = "2026-01-01")
    private LocalDate effectiveStartDate;

    @Schema(description = "有效期结束", example = "2026-12-31")
    private LocalDate effectiveEndDate;

    @Schema(description = "有效期类型 1-永久 2-自定义", example = "1")
    private Integer effectiveType;

    @Schema(description = "是否强制覆盖", example = "false")
    private Boolean force;
}
