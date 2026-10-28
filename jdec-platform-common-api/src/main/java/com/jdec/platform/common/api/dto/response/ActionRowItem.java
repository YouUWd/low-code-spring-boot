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
 * 菜单操作与互动按钮行信息模型 (保持与前端 row 契约 100% 兼容)
 *
 * <ul>
 *   <li>{@link #ofGlobal(List)}: 构建与行数据无关的全局操作项 (无 id, 无 businessNo)
 *   <li>{@link #ofRow(Long, String, List)}: 构建与具体单据行绑定的行级操作项
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "菜单操作按钮行信息包装模型 (row包含可选的id、businessNo与btnList)")
public class ActionRowItem implements Serializable {

    @Schema(description = "行数据载荷")
    private ActionRowPayload row;

    /**
     * 构建与行数据无关的全局操作项 (无 id, 无 businessNo)
     *
     * @param buttons 全局按钮列表
     * @return 仅包含 btnList 的行信息模型
     */
    public static ActionRowItem ofGlobal(List<ActionButtonDTO> buttons) {
        return ActionRowItem.builder()
                .row(ActionRowPayload.builder().btnList(buttons).build())
                .build();
    }

    /**
     * 构建与具体单据行绑定的行级操作项
     *
     * @param id 行记录主键ID
     * @param businessNo 单据业务单号
     * @param buttons 当前行按钮列表
     * @return 包含 id、businessNo 与 btnList 的行信息模型
     */
    public static ActionRowItem ofRow(Long id, String businessNo, List<ActionButtonDTO> buttons) {
        return ActionRowItem.builder()
                .row(
                        ActionRowPayload.builder()
                                .id(id)
                                .businessNo(businessNo)
                                .btnList(buttons)
                                .build())
                .build();
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public Long getId() {
        return row != null ? row.getId() : null;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getBusinessNo() {
        return row != null ? row.getBusinessNo() : null;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public List<ActionButtonDTO> getBtnList() {
        return row != null ? row.getBtnList() : null;
    }
}
