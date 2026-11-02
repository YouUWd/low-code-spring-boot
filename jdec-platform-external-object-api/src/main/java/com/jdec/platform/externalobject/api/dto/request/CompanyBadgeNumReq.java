package com.jdec.platform.externalobject.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 外部对象菜单角标请求 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "外部对象菜单角标请求")
public class CompanyBadgeNumReq {

    @NotNull(message = "单据 ID (id) 不能为空")
    @Schema(description = "单据物理主键 ID", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @NotNull(message = "顶层菜单ID (rootMenuId) 不能为空")
    @Schema(
            description = "顶层菜单物理主键 ID",
            example = "19",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long rootMenuId;

    @NotNull(message = "待获取角标数据的菜单ID集合")
    @Schema(
            description = "待获取角标数据的菜单ID集合",
            example = "[20,21]",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> menuIds;
}
