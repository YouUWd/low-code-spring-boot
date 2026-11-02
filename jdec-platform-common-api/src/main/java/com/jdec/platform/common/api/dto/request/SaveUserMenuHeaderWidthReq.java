package com.jdec.platform.common.api.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/**
 * 保存用户个性化表头列宽请求（列表拖动列宽）
 *
 * <p>只定位单列：归属菜单 + 节点类型 + 来源ID（{@code virId} 由类型与来源ID 按菜单 {@code MENU_菜单ID}、字段 {@code Field_字段ID}
 * 的规则拼装，来源ID 取表头节点返回的 {@code sourceId}），{@code dataIndex} 仅在无法按 {@code virId} 命中时作兜底匹配。
 *
 * <p>用户与角色取当前请求上下文，不由前端传递。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "保存用户个性化表头列宽请求")
public class SaveUserMenuHeaderWidthReq implements Serializable {

    @Schema(description = "列表菜单ID（与表头查询/保存接口同一入参口径）", example = "1")
    private Long menuId;

    @Schema(description = "节点类型：0-菜单，1-字段（仅字段允许设置列宽）", example = "1")
    private Integer nodeType;

    @Schema(description = "来源ID（nodeType=0 时=来源菜单ID，nodeType=1 时=来源字段ID）", example = "10")
    private Long sourceId;

    @Schema(description = "字段索引（表名.字段名），用于 virId 未命中时的兜底匹配", example = "info.company_name")
    private String dataIndex;

    @Schema(description = "列宽(px)", example = "180")
    private Integer width;
}
