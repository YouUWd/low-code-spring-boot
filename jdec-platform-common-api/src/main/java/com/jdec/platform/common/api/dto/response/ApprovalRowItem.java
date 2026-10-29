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
 * 历史单据操作行模型 (已升级为强类型 {@link ActionRowPayload}，建议直接使用 {@link ActionRowItem})
 *
 * @deprecated 建议迁移至强类型模型 {@link ActionRowItem}
 */
@Deprecated
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "单据操作与互动行信息模型 (历史兼容模型，建议迁移使用 ActionRowItem)")
public class ApprovalRowItem implements Serializable {

    @Schema(description = "行数据载荷")
    private ActionRowPayload row;

    public static ApprovalRowItem ofGlobal(List<ActionButtonDTO> buttons) {
        return ApprovalRowItem.builder()
                .row(ActionRowPayload.builder().btnList(buttons).build())
                .build();
    }

    public static ApprovalRowItem ofRow(Long id, String businessNo, List<ActionButtonDTO> buttons) {
        return ApprovalRowItem.builder()
                .row(
                        ActionRowPayload.builder()
                                .id(id)
                                .businessNo(businessNo)
                                .btnList(buttons)
                                .build())
                .build();
    }

    public ActionRowItem toActionRowItem() {
        return ActionRowItem.builder().row(this.row).build();
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
