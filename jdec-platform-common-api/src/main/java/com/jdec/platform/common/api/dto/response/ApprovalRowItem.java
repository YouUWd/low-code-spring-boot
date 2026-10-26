package com.jdec.platform.common.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 单条单据审批行信息模型 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "单据审批行信息模型")
public class ApprovalRowItem implements Serializable {

    @Schema(description = "审批行数据载荷 (包含 id 与 btnList)")
    private Map<String, Object> row;
}
