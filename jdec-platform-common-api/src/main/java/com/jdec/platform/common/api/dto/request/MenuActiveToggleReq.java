package com.jdec.platform.common.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 基于菜单的业务单据批量启用/停用请求 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "基于菜单的业务单据批量启用/停用请求")
public class MenuActiveToggleReq {

    @NotEmpty(message = "业务编号列表 businessNos 不能为空")
    @Schema(
            description = "待更新的单据业务编号列表 (business_no)",
            example = "[\"CO202610290001\", \"CO202610290002\"]",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> businessNos;

    @Schema(description = "目标业务状态: 99-启用(将11改为99), 11-停用(将99改为11)", example = "99")
    private Integer targetStatus;

    @Schema(description = "启用标识: 1-启用, 0-停用 (与 targetStatus 互通)", example = "1")
    private Integer active;

    @Schema(
            description = "操作动作标识: activate(启用), deactivate(停用) (与 targetStatus 互通)",
            example = "activate")
    private String action;

    /**
     * 判断当前请求是否为启用操作
     *
     * @return true-启用(11改为99), false-停用(99改为11)
     * @throws IllegalArgumentException 当未指定有效的目标状态或动作时抛出
     */
    public boolean isActivate() {
        if (Integer.valueOf(99).equals(targetStatus)
                || Integer.valueOf(1).equals(active)
                || "activate".equalsIgnoreCase(action)) {
            return true;
        }
        if (Integer.valueOf(11).equals(targetStatus)
                || Integer.valueOf(0).equals(active)
                || "deactivate".equalsIgnoreCase(action)) {
            return false;
        }
        throw new IllegalArgumentException(
                "未指定有效的启用/停用操作目标 (targetStatus 需为 99 或 11, active 需为 1 或 0, 或 action 需为 activate/deactivate)");
    }
}
