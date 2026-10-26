package com.jdec.platform.hr.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record HelloResp(
        @Schema(description = "欢迎信息", example = "欢迎光临") String msg,
        @Schema(description = "秘密", example = "秘密") String secret) {}
