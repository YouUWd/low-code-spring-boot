package com.jdec.platform.data.api.dto.response;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 动态列表表头配置响应模型 (精简字段元数据，预留后续扩展) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "动态列表表头配置响应模型")
public class EngineHeaderResp implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "表头字段元数据列表")
    private List<ModuleFieldDTO> fields;
}
