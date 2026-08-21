package com.jdec.platform.config.api.engine.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;

@Data
@Schema(description = "动态数据更新请求")
public class DataUpdateReq {
    @Schema(description = "更新数据内容 (字段名 -> 新值)", requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<String, Object> data;
}
