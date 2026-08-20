package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色关联主体请求
 *
 * <p>包含主体ID和时效信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "角色关联主体请求")
public class RoleSubjectReq {

    @NotNull(message = "主体ID不能为空")
    @Schema(description = "主体ID", example = "100")
    private Long subjectId;

    @Schema(description = "有效期开始", example = "2026-01-01")
    private LocalDate effectiveStartDate;

    @Schema(description = "有效期结束", example = "2026-12-31")
    private LocalDate effectiveEndDate;

    @Schema(description = "有效期类型 1-永久 2-自定义", example = "1")
    private Integer effectiveType;

    @Schema(description = "状态(0-有效，1-失效)", example = "0")
    private Integer status;
}
