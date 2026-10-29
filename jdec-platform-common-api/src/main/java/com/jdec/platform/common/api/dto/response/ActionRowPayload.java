package com.jdec.platform.common.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜单操作按钮行数据载荷 (面向对象强类型模型)
 *
 * <ul>
 *   <li>全局操作：{@code id} 与 {@code businessNo} 为空，仅输出 {@code btnList}
 *   <li>行级操作：包含当前行的 {@code id}、{@code businessNo} 以及当前行适用的 {@code btnList}
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "菜单操作按钮行数据载荷 (包含单据标识与按钮列表)")
public class ActionRowPayload implements Serializable {

    @Schema(description = "行记录主键 ID (全局操作为空)", example = "1001")
    private Long id;

    @Schema(description = "单据业务编号 (全局操作为空)", example = "CR20261027001")
    private String businessNo;

    @Schema(description = "当前行操作按钮列表")
    private List<ActionButtonDTO> btnList;
}
