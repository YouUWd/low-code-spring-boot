package com.jdec.platform.data.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 多模块批量并发查询响应模型 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "多模块批量查询响应模型")
public class BatchEngineDataResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "多模块查询结果映射，Key 与请求中的别名一致")
    private Map<String, EngineDataResult<DataPage<Map<String, Object>>>> results;

    public static BatchEngineDataResult of(
            Map<String, EngineDataResult<DataPage<Map<String, Object>>>> results) {
        return new BatchEngineDataResult(results);
    }
}
