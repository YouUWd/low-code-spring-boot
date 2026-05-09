package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Schema(description = "角色配置详情")
@Data
public class RoleConfigDetailResp {
    @Schema(description = "角色ID")
    private Long id;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "主体ID")
    private List<Long> subjectIds;

    @Schema(description = "生效开始日期")
    private LocalDate effectiveStartDate;

    @Schema(description = "生效结束日期")
    private LocalDate effectiveEndDate;

    @Schema(description = "用户ID列表")
    private List<String> userIds;
}
