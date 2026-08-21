package com.jdec.platform.engine.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;

@Data
@Schema(description = "动态数据详情响应")
public class DataDetailResp {
    @Schema(description = "数据内容")
    private Map<String, Object> data;
}
