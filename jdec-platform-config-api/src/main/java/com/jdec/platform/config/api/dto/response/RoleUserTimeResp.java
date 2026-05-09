package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;

@Data
@Schema(description = "选中用户的时效")
public class RoleUserTimeResp {
    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "用户名称")
    private LocalDate effectiveStartDate;

    @Schema(description = "用户名称")
    private LocalDate effectiveEndDate;
}
