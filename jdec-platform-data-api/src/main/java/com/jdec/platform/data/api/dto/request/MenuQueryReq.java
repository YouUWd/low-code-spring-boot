package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 基于菜单的动态列表分页/过滤查询请求模型 (面向前端 UI 隔离层)
 *
 * <p>前端仅需按需传递分页、过滤和排序规则，根模块 ID 与投影字段统一由后端通过 menuId 和表头配置自动解析推导。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "基于菜单的动态列表分页/过滤查询请求模型")
public class MenuQueryReq implements Serializable {

    @Schema(description = "页码 (从 1 开始，针对当前模块列表)", example = "1")
    private Integer pageNo;

    @Schema(description = "每页条数 / 明细上限条数", example = "20")
    private Integer pageSize;

    @Schema(description = "平铺过滤条件列表")
    private List<FilterItem> filters;

    @Schema(description = "平铺排序规则列表")
    private List<SortItem> sorts;
}
