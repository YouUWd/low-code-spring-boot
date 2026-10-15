package com.jdec.platform.common.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 菜单审批与角标统计响应模型 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "菜单审批统计响应模型")
public class MenuApprovalCountResp implements Serializable {

    @Schema(description = "状态名称 (待办/已办/全部)", example = "待办")
    private String label;

    @Schema(description = "审批过滤类型: 1-待办, 2-已办, 3-全部", example = "1")
    private Integer type;

    @Schema(description = "数量统计", example = "12")
    private Long count;

    public static MenuApprovalCountResp of(String label, Integer type, Long count) {
        return MenuApprovalCountResp.builder()
                .label(label)
                .type(type)
                .count(count != null ? count : 0L)
                .build();
    }
}
