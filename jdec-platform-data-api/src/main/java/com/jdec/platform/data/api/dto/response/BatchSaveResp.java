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

    @Schema(description = "各模块保存结果字典 (Key 为 moduleId，Value 为落库主键 ID 或执行状态)")
    private Map<Long, Object> results;

    public static BatchSaveResp of(Map<Long, Object> results) {
        return new BatchSaveResp(results);
    }
}
