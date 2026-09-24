package com.jdec.platform.common.biz.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 基于菜单的字段搜索下拉候选项请求 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "基于菜单的字段搜索下拉候选项请求参数")
public class MenuOptionReq implements Serializable {

    @NotNull(message = "字段 ID (fieldId) 不能为空")
    @Schema(
            description = "目标字段 ID (sys_module_field.id)，必须属于当前菜单关联的根模块或其子孙模块",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "1031")
    private Long fieldId;

    @Schema(description = "纯平铺单字段过滤条件列表 (默认以平级 AND 组合)")
    private List<FlatFilterItem> filters;

    @Schema(description = "候选项排序方向: ASC (升序) / DESC (降序)，为 null 或空时默认不进行额外排序", example = "ASC")
    private String direction;

    @Schema(description = "审批流过滤类型: 1-待办, 2-已办, 3-全部 (可选，默认3)", example = "3")
    private Integer approvalType;
}
