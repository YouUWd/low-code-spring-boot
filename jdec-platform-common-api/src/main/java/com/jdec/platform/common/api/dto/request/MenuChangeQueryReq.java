package com.jdec.platform.common.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 基于菜单的变更申请单动态列表分页/过滤查询请求模型 (面向前端 UI 隔离层)
 *
 * <p>变更申请列表中无审批流类型过滤，支持按已读状态过滤 (readStatus: 0-未读, 1-已读, 2或null-全部)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "基于菜单的变更申请单动态列表分页/过滤查询请求模型")
public class MenuChangeQueryReq implements Serializable {

    @Schema(description = "页码 (从 1 开始)", example = "1")
    private Integer pageNo;

    @Schema(description = "每页条数", example = "20")
    private Integer pageSize;

    @Schema(description = "纯平铺单字段过滤条件列表 (默认以平级 AND 组合)")
    private List<MenuFlatFieldFilterItem> filters;

    @Schema(description = "平铺排序规则列表")
    private List<FieldSortItem> sorts;

    @Schema(description = "已读状态过滤: 0-未读, 1-已读, 2-全部 (可选，默认全部)", example = "0")
    private Integer readStatus;
}
