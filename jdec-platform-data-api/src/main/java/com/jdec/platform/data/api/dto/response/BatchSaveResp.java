package com.jdec.platform.data.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 多模块同构原子批量保存响应模型 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "多模块批量保存响应模型")
public class BatchSaveResp implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主模块落库生成/确认的主键 ID", example = "1001")
    private Long masterId;

    @Schema(description = "各子模块处理结果字典 (Key 为子模块别名，Value 为受影响记录数或处理信息)")
    private Map<String, Object> childResults;

    public static BatchSaveResp of(Long masterId, Map<String, Object> childResults) {
        return new BatchSaveResp(masterId, childResults);
    }
}
