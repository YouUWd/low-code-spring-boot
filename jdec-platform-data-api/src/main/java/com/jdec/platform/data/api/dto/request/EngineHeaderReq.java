package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 动态列表表头配置查询请求 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "动态列表表头配置查询请求")
public class EngineHeaderReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "模块 ID (可选)", example = "101")
    private Long moduleId;

    @Schema(description = "字段 ID 列表 (sys_module_field.id)")
    private List<Long> fields;
}
