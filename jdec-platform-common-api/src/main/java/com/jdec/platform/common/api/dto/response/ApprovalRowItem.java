package com.jdec.platform.common.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单据操作与互动行信息模型 (兼容行数据绑定与全局操作)
 *
 * <p>面向对象封装：
 *
 * <ul>
 *   <li>行无关操作 (全局)：row 载荷仅包含 btnList，不输出 id 与 businessNo
 *   <li>行相关操作 (行绑定)：row 载荷包含 id、businessNo 与对应的 btnList
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "单据操作与互动行信息模型 (row包含可选的id、businessNo与btnList)")
public class ApprovalRowItem implements Serializable {

    @Schema(description = "行数据载荷 (包含可选的 id、businessNo 与 btnList)")
    private Map<String, Object> row;

    /**
     * 构建与行数据无关的全局操作项 (无 id, 无 businessNo)
     *
     * @param buttons 全局按钮列表
     * @return 仅包含 btnList 的行信息模型
     */
    public static ApprovalRowItem ofGlobal(List<ActionButtonDTO> buttons) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("btnList", buttons);
        return ApprovalRowItem.builder().row(payload).build();
    }

    /**
     * 构建与具体单据行绑定的行级操作项
     *
     * @param id 行记录主键ID
     * @param businessNo 单据业务单号
     * @param buttons 当前行按钮列表
     * @return 包含 id、businessNo 与 btnList 的行信息模型
     */
    public static ApprovalRowItem ofRow(Long id, String businessNo, List<ActionButtonDTO> buttons) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (id != null) {
            payload.put("id", id);
        }
        if (businessNo != null && !businessNo.isBlank()) {
            payload.put("businessNo", businessNo);
        }
        payload.put("btnList", buttons);
        return ApprovalRowItem.builder().row(payload).build();
    }
}
