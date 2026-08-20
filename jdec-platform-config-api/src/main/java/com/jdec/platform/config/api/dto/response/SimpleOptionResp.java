package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 通用简单选项响应（仅包含 id 和 name） */
@Data
@Schema(description = "通用简单选项响应")
public class SimpleOptionResp {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "名称")
    private String name;
}
