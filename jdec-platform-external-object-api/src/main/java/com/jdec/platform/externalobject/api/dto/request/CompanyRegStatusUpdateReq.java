package com.jdec.platform.externalobject.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 修改外部对象单据登记状态请求 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "修改外部对象单据登记状态请求")
public class CompanyRegStatusUpdateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "单据 ID (id) 不能为空")
    @Schema(description = "单据物理主键 ID", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @NotNull(message = "登记状态 regStatus 不能为空")
    @Schema(
            description = "目标登记状态 JSON (支持 JSON 数组、对象或普通字符串/词条配置)",
            example = "[{\"word_describe\":\"存续\",\"word_id\":455}]",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Object regStatus;
}
